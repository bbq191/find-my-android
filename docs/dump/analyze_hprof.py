"""
分析转换后的标准 Java hprof 文件，提取 retained objects 信息
重点查找：Activity/Fragment/Service 泄漏、CoroutineScope 泄漏

hprof 二进制格式参考：
http://hg.openjdk.java.net/jdk6/jdk6/jdk/raw-file/tip/src/share/demo/jvmti/hprof/manual.html

author: afu
"""

import struct
import sys
from collections import Counter, defaultdict

# hprof tag 常量
TAG_STRING = 0x01
TAG_LOAD_CLASS = 0x02
TAG_HEAP_DUMP = 0x0C
TAG_HEAP_DUMP_SEGMENT = 0x1C

# heap dump sub-tag
SUB_ROOT_JNI_GLOBAL = 0x01
SUB_ROOT_JNI_LOCAL = 0x02
SUB_ROOT_JAVA_FRAME = 0x03
SUB_ROOT_NATIVE_STACK = 0x04
SUB_ROOT_STICKY_CLASS = 0x05
SUB_ROOT_THREAD_BLOCK = 0x06
SUB_ROOT_MONITOR_USED = 0x07
SUB_ROOT_THREAD_OBJ = 0x08
SUB_CLASS_DUMP = 0x20
SUB_INSTANCE_DUMP = 0x21
SUB_OBJ_ARRAY_DUMP = 0x22
SUB_PRIM_ARRAY_DUMP = 0x23

# 基本类型大小
BASIC_TYPE_SIZES = {
    2: 4,   # object
    4: 1,   # boolean
    5: 2,   # char
    6: 4,   # float
    7: 8,   # double
    8: 1,   # byte
    9: 2,   # short
    10: 4,  # int
    11: 8,  # long
}


def analyze_hprof(filepath):
    print(f"=== 分析文件: {filepath} ===\n")

    strings = {}        # id -> string
    class_names = {}    # class_serial -> name_id
    class_id_to_serial = {}  # class_obj_id -> serial
    class_id_to_name = {}    # class_obj_id -> class_name
    id_size = 4

    # 统计
    instance_counts = Counter()   # class_name -> count
    gc_roots = []                 # (root_type, object_id)
    activity_instances = []       # (obj_id, class_name)
    service_instances = []
    scope_instances = []
    view_instances = []

    # 类继承关系
    class_super = {}             # class_id -> super_class_id

    with open(filepath, 'rb') as f:
        # 读取 header
        header = b''
        while True:
            b = f.read(1)
            if b == b'\x00':
                break
            header += b

        print(f"Header: {header.decode('utf-8', errors='replace')}")

        id_size = struct.unpack('>I', f.read(4))[0]
        timestamp = struct.unpack('>Q', f.read(8))[0]

        print(f"ID size: {id_size} bytes")
        print(f"Timestamp: {timestamp}\n")

        def read_id():
            if id_size == 4:
                return struct.unpack('>I', f.read(4))[0]
            else:
                return struct.unpack('>Q', f.read(8))[0]

        record_count = 0
        heap_records = 0

        while True:
            tag_bytes = f.read(1)
            if not tag_bytes:
                break

            tag = struct.unpack('B', tag_bytes)[0]
            timestamp = struct.unpack('>I', f.read(4))[0]
            length = struct.unpack('>I', f.read(4))[0]

            record_count += 1

            if tag == TAG_STRING:
                str_id = read_id()
                str_data = f.read(length - id_size)
                strings[str_id] = str_data.decode('utf-8', errors='replace')

            elif tag == TAG_LOAD_CLASS:
                serial = struct.unpack('>I', f.read(4))[0]
                class_obj_id = read_id()
                stack_serial = struct.unpack('>I', f.read(4))[0]
                name_id = read_id()
                class_names[serial] = name_id
                class_id_to_serial[class_obj_id] = serial
                if name_id in strings:
                    class_id_to_name[class_obj_id] = strings[name_id]

            elif tag in (TAG_HEAP_DUMP, TAG_HEAP_DUMP_SEGMENT):
                heap_records += 1
                end_pos = f.tell() + length

                while f.tell() < end_pos:
                    sub_tag = struct.unpack('B', f.read(1))[0]

                    if sub_tag == SUB_ROOT_JNI_GLOBAL:
                        obj_id = read_id()
                        jni_ref = read_id()
                        gc_roots.append(('JNI_GLOBAL', obj_id))

                    elif sub_tag == SUB_ROOT_JNI_LOCAL:
                        obj_id = read_id()
                        thread_serial = struct.unpack('>I', f.read(4))[0]
                        frame_num = struct.unpack('>I', f.read(4))[0]
                        gc_roots.append(('JNI_LOCAL', obj_id))

                    elif sub_tag == SUB_ROOT_JAVA_FRAME:
                        obj_id = read_id()
                        thread_serial = struct.unpack('>I', f.read(4))[0]
                        frame_num = struct.unpack('>I', f.read(4))[0]
                        gc_roots.append(('JAVA_FRAME', obj_id))

                    elif sub_tag == SUB_ROOT_NATIVE_STACK:
                        obj_id = read_id()
                        thread_serial = struct.unpack('>I', f.read(4))[0]
                        gc_roots.append(('NATIVE_STACK', obj_id))

                    elif sub_tag == SUB_ROOT_STICKY_CLASS:
                        obj_id = read_id()
                        gc_roots.append(('STICKY_CLASS', obj_id))

                    elif sub_tag == SUB_ROOT_THREAD_BLOCK:
                        obj_id = read_id()
                        thread_serial = struct.unpack('>I', f.read(4))[0]
                        gc_roots.append(('THREAD_BLOCK', obj_id))

                    elif sub_tag == SUB_ROOT_MONITOR_USED:
                        obj_id = read_id()
                        gc_roots.append(('MONITOR_USED', obj_id))

                    elif sub_tag == SUB_ROOT_THREAD_OBJ:
                        obj_id = read_id()
                        thread_serial = struct.unpack('>I', f.read(4))[0]
                        stack_serial = struct.unpack('>I', f.read(4))[0]
                        gc_roots.append(('THREAD_OBJ', obj_id))

                    elif sub_tag == SUB_CLASS_DUMP:
                        class_id = read_id()
                        stack_serial = struct.unpack('>I', f.read(4))[0]
                        super_class_id = read_id()
                        class_super[class_id] = super_class_id
                        loader_id = read_id()
                        signers_id = read_id()
                        prot_domain_id = read_id()
                        reserved1 = read_id()
                        reserved2 = read_id()
                        instance_size = struct.unpack('>I', f.read(4))[0]

                        # 常量池
                        cp_count = struct.unpack('>H', f.read(2))[0]
                        for _ in range(cp_count):
                            idx = struct.unpack('>H', f.read(2))[0]
                            tp = struct.unpack('B', f.read(1))[0]
                            f.read(BASIC_TYPE_SIZES.get(tp, id_size))

                        # 静态字段
                        sf_count = struct.unpack('>H', f.read(2))[0]
                        for _ in range(sf_count):
                            name_id = read_id()
                            tp = struct.unpack('B', f.read(1))[0]
                            f.read(BASIC_TYPE_SIZES.get(tp, id_size))

                        # 实例字段
                        if_count = struct.unpack('>H', f.read(2))[0]
                        for _ in range(if_count):
                            name_id = read_id()
                            tp = struct.unpack('B', f.read(1))[0]

                    elif sub_tag == SUB_INSTANCE_DUMP:
                        obj_id = read_id()
                        stack_serial = struct.unpack('>I', f.read(4))[0]
                        class_id = read_id()
                        data_len = struct.unpack('>I', f.read(4))[0]
                        f.read(data_len)  # skip instance data

                        class_name = class_id_to_name.get(class_id, f'unknown_{class_id:#x}')
                        instance_counts[class_name] += 1

                        # 检查关键类
                        cn_lower = class_name.lower()
                        if 'activity' in cn_lower and 'me/ikate/findmy' in class_name:
                            activity_instances.append((obj_id, class_name))
                        elif 'service' in cn_lower and 'me/ikate/findmy' in class_name:
                            service_instances.append((obj_id, class_name))
                        elif 'coroutinescope' in cn_lower or 'supervisorjob' in cn_lower or 'jobimpl' in cn_lower:
                            scope_instances.append((obj_id, class_name))
                        elif ('view' in cn_lower or 'fragment' in cn_lower) and 'me/ikate/findmy' in class_name:
                            view_instances.append((obj_id, class_name))

                    elif sub_tag == SUB_OBJ_ARRAY_DUMP:
                        obj_id = read_id()
                        stack_serial = struct.unpack('>I', f.read(4))[0]
                        num_elements = struct.unpack('>I', f.read(4))[0]
                        array_class_id = read_id()
                        f.read(num_elements * id_size)

                    elif sub_tag == SUB_PRIM_ARRAY_DUMP:
                        obj_id = read_id()
                        stack_serial = struct.unpack('>I', f.read(4))[0]
                        num_elements = struct.unpack('>I', f.read(4))[0]
                        elem_type = struct.unpack('B', f.read(1))[0]
                        elem_size = BASIC_TYPE_SIZES.get(elem_type, 1)
                        f.read(num_elements * elem_size)

                    elif sub_tag == 0xFF:
                        # ROOT_UNKNOWN
                        obj_id = read_id()
                        gc_roots.append(('UNKNOWN', obj_id))

                    elif sub_tag == 0x89:
                        # ROOT_INTERNED_STRING (Android)
                        obj_id = read_id()

                    elif sub_tag == 0x8B:
                        # ROOT_DEBUGGER (Android)
                        obj_id = read_id()

                    elif sub_tag == 0x8D:
                        # ROOT_VM_INTERNAL (Android)
                        obj_id = read_id()

                    elif sub_tag == 0xFE:
                        # HEAP_DUMP_INFO (Android)
                        heap_type = struct.unpack('>I', f.read(4))[0]
                        name_id = read_id()

                    elif sub_tag == 0xC3:
                        # ROOT_REFERENCE_CLEANUP (Android specific)
                        obj_id = read_id()

                    elif sub_tag == 0x8A:
                        # ROOT_FINALIZING
                        obj_id = read_id()

                    elif sub_tag == 0x90:
                        # ROOT_UNREACHABLE
                        obj_id = read_id()

                    else:
                        # 未知 sub_tag，跳到段末尾
                        print(f"  [WARN] 未知 sub_tag: 0x{sub_tag:02X} at offset {f.tell():#x}, 跳过剩余段")
                        f.seek(end_pos)
                        break
            else:
                f.read(length)

    # ====== 输出分析结果 ======
    print(f"总记录数: {record_count}")
    print(f"Heap dump 段数: {heap_records}")
    print(f"字符串数: {len(strings)}")
    print(f"类数: {len(class_id_to_name)}")
    print(f"GC roots 数: {len(gc_roots)}")
    print()

    # 1. findmy 包下的所有实例
    print("=" * 70)
    print("== [findmy 包下的实例统计] ==")
    print("=" * 70)
    findmy_classes = {k: v for k, v in instance_counts.items() if 'me/ikate/findmy' in k}
    for name, count in sorted(findmy_classes.items(), key=lambda x: -x[1])[:50]:
        short_name = name.replace('me/ikate/findmy/', '')
        print(f"  {count:>5}x  {short_name}")

    print()

    # 2. Activity 实例
    print("=" * 70)
    print("== [Activity 实例] (存在多个实例可能暗示泄漏) ==")
    print("=" * 70)
    activity_counter = Counter(name for _, name in activity_instances)
    for name, count in activity_counter.most_common():
        short_name = name.replace('me/ikate/findmy/', '')
        leaked = " *** 可能泄漏 ***" if count > 1 else ""
        print(f"  {count:>3}x  {short_name}{leaked}")

    print()

    # 3. Service 实例
    print("=" * 70)
    print("== [Service 实例] ==")
    print("=" * 70)
    service_counter = Counter(name for _, name in service_instances)
    for name, count in service_counter.most_common():
        short_name = name.replace('me/ikate/findmy/', '')
        leaked = " *** 可能泄漏 ***" if count > 1 else ""
        print(f"  {count:>3}x  {short_name}{leaked}")

    print()

    # 4. CoroutineScope / Job 实例
    print("=" * 70)
    print("== [CoroutineScope / Job 实例] ==")
    print("=" * 70)
    scope_counter = Counter(name for _, name in scope_instances)
    for name, count in scope_counter.most_common()[:20]:
        print(f"  {count:>5}x  {name}")

    print()

    # 5. View/Fragment 实例
    print("=" * 70)
    print("== [findmy View/Fragment 实例] ==")
    print("=" * 70)
    view_counter = Counter(name for _, name in view_instances)
    for name, count in view_counter.most_common()[:20]:
        short_name = name.replace('me/ikate/findmy/', '')
        print(f"  {count:>3}x  {short_name}")

    print()

    # 6. 关键的泄漏嫌疑类
    print("=" * 70)
    print("== [泄漏嫌疑类 - 关键单例/管理器实例数] ==")
    print("=" * 70)
    suspect_keywords = [
        'MqttConnectionManager', 'LocationMqttService', 'MqttForegroundService',
        'SmartLocationSyncManager', 'LocationReportService', 'DeviceRepository',
        'ContactRepository', 'AuthRepository', 'CommunicationManager',
        'SmartLocator', 'TencentLocationService', 'TencentLocationManager',
        'GeofenceManager', 'LocationStateMachine', 'SoundPlaybackService',
        'LostModeService', 'MainViewModel', 'ContactViewModel',
    ]
    for keyword in suspect_keywords:
        matches = {k: v for k, v in instance_counts.items() if keyword in k}
        if matches:
            for name, count in sorted(matches.items(), key=lambda x: -x[1]):
                flag = " *** 应为单例但有多个 ***" if count > 1 and any(s in name for s in ['Manager', 'Service', 'Repository', 'Singleton', 'ViewModel']) else ""
                print(f"  {count:>3}x  {name}{flag}")

    print()

    # 7. GC root 类型分布
    print("=" * 70)
    print("== [GC Root 类型分布] ==")
    print("=" * 70)
    root_counter = Counter(rtype for rtype, _ in gc_roots)
    for rtype, count in root_counter.most_common():
        print(f"  {count:>6}x  {rtype}")

    print()
    print("=== 分析完成 ===\n")


if __name__ == '__main__':
    # 分析原始 Android hprof（非转换后的，因为原始格式包含 Android 特有的标签）
    # 但我们已经转换了，用转换后的也可以
    analyze_hprof(r'e:\my-projects\findmy\docs\dump\converted_1.hprof')
    print("\n" + "=" * 80 + "\n")
    analyze_hprof(r'e:\my-projects\findmy\docs\dump\converted_2.hprof')
