# CoroutineWorker 取消机制与清理策略

## 重要提醒

⚠️ **`CoroutineWorker.onStopped()` 方法是 final 的，无法重写！**

正确的做法是利用 Kotlin 协程的取消机制来处理清理工作。

## 正确的清理方式

### 1. 使用 try-finally 块

```kotlin
override suspend fun doWork(): Result {
    return try {
        var updateCount = 0

        try {
            // 主要工作逻辑
            while (shouldContinue()) {
                if (isStopped) break  // 检查手动停止

                doSomeWork()
                updateCount++
                delay(1000)
            }

            Result.success()
        } catch (e: CancellationException) {
            // 协程被取消时的处理
            Log.d(TAG, "任务被取消")
            throw e  // ⚠️ 必须重新抛出！
        } finally {
            // 清理工作（总是执行）
            cleanup(updateCount)
        }
    } catch (e: CancellationException) {
        // 重新抛出，确保取消传播
        throw e
    } catch (e: Exception) {
        // 其他异常处理
        Result.failure()
    }
}
```

### 2. ContinuousLocationWorker 的实现

```kotlin
override suspend fun doWork(): Result {
    val requesterUid = inputData.getString("requesterUid") ?: "unknown"

    return try {
        setForeground(createForegroundInfo())

        val endTime = System.currentTimeMillis() + TRACKING_DURATION_MS
        var updateCount = 0

        try {
            // 第一次立即上报
            reportLocation(++updateCount)

            // 循环上报
            while (System.currentTimeMillis() < endTime) {
                // ✅ 检查是否被取消
                if (isStopped) {
                    Log.d(TAG, "追踪被手动停止")
                    break
                }

                delay(UPDATE_INTERVAL_MS)

                if (System.currentTimeMillis() >= endTime) {
                    Log.d(TAG, "追踪时间到，自动停止")
                    break
                }

                reportLocation(++updateCount)
            }

            Log.d(TAG, "追踪完成，共上报 $updateCount 次")
            sendDebugNotification("追踪结束", "已完成 $updateCount 次更新")

            Result.success()
        } catch (e: CancellationException) {
            // ⚠️ 协程被取消（WorkManager.cancelUniqueWork）
            Log.d(TAG, "任务被取消，共上报 $updateCount 次")
            sendDebugNotification("追踪已停止", "任务被取消")
            throw e  // 必须重新抛出
        } finally {
            // ✅ 清理工作（总是执行）
            Log.d(TAG, "执行清理工作（共上报 $updateCount 次）")
        }
    } catch (e: CancellationException) {
        // 重新抛出，确保取消传播到 WorkManager
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "追踪失败", e)
        sendDebugNotification("追踪异常", "错误: ${e.message}")
        Result.failure()
    }
}
```

## 关键要点

### 1. CancellationException 必须重新抛出

```kotlin
catch (e: CancellationException) {
    // 处理取消逻辑
    cleanup()
    throw e  // ⚠️ 必须！否则协程取消会失败
}
```

**原因**：
- `CancellationException` 是协程取消的信号
- 吞掉这个异常会导致取消失败
- WorkManager 依赖这个异常来正确处理 Worker 取消

### 2. isStopped 的作用

`isStopped` 是 `ListenableWorker` 的属性，表示 Worker 是否已被请求停止。

```kotlin
while (shouldContinue()) {
    if (isStopped) {
        // 提前退出循环
        break
    }
    // 继续工作
}
```

**使用场景**：
- 在长时间循环中定期检查
- 优雅地提前退出
- 配合 `delay()` 使用时，delay 会自动响应取消

### 3. finally 块的作用

```kotlin
try {
    // 工作逻辑
} catch (e: CancellationException) {
    throw e
} finally {
    // 无论如何都会执行
    closeResources()
    saveProgress()
}
```

**保证**：
- 正常完成时执行
- 抛出异常时执行
- 被取消时执行

## Worker 取消的三种方式

### 1. 用户主动取消

```kotlin
// 在 Service 或 ViewModel 中
WorkManager.getInstance(context)
    .cancelUniqueWork("continuous_location_tracking")
```

**效果**：
- Worker 的协程被取消
- `isStopped` 变为 `true`
- 抛出 `CancellationException`

### 2. 超时自动停止

```kotlin
// Worker 内部
val endTime = System.currentTimeMillis() + 60_000
while (System.currentTimeMillis() < endTime) {
    // 工作...
}
// 自然结束，返回 Result.success()
```

**效果**：
- 正常退出循环
- 执行 finally 块
- 返回 Result.success()

### 3. 系统杀掉 Worker

**场景**：
- 内存不足
- 应用被强制停止
- 设备重启

**效果**：
- Worker 进程被杀
- 无法执行清理代码
- 下次启动时需要检查状态

## 实际测试验证

### 测试1：正常完成

```bash
# Logcat 输出
D/ContinuousLocationWorker: 🎯 开始短时实时追踪
D/ContinuousLocationWorker: 📍 第 1 次位置上报成功
D/ContinuousLocationWorker: 📍 第 2 次位置上报成功
...
D/ContinuousLocationWorker: 📍 第 8 次位置上报成功
D/ContinuousLocationWorker: ⏱️ 追踪时间到，自动停止
D/ContinuousLocationWorker: ✅ 追踪完成，共上报 8 次
D/ContinuousLocationWorker: 🧹 执行清理工作（共上报 8 次）
```

### 测试2：用户手动停止

```bash
# 点击 UI 的"停止"按钮后
D/ContinuousLocationWorker: 📍 第 3 次位置上报成功
D/ContinuousLocationWorker: ⏹️ 追踪被手动停止
D/ContinuousLocationWorker: ⏹️ 任务被取消，共上报 3 次
D/ContinuousLocationWorker: 🧹 执行清理工作（共上报 3 次）
```

### 测试3：WorkManager 取消

```bash
# 调用 cancelUniqueWork 后
D/ContinuousLocationWorker: 📍 第 5 次位置上报成功
# delay() 被中断，抛出 CancellationException
D/ContinuousLocationWorker: ⏹️ 任务被取消，共上报 5 次
D/ContinuousLocationWorker: 🧹 执行清理工作（共上报 5 次）
```

## 常见错误

### ❌ 错误1：吞掉 CancellationException

```kotlin
try {
    delay(1000)
} catch (e: Exception) {  // 捕获所有异常
    Log.e(TAG, "错误", e)
    // 没有重新抛出！
}
```

**问题**：`CancellationException` 被 `Exception` 捕获并吞掉，导致取消失败。

**修复**：
```kotlin
try {
    delay(1000)
} catch (e: CancellationException) {
    throw e  // 先重新抛出
} catch (e: Exception) {
    Log.e(TAG, "错误", e)
}
```

### ❌ 错误2：尝试重写 onStopped

```kotlin
override suspend fun onStopped() {  // ❌ 编译错误！
    cleanup()
}
```

**问题**：`onStopped()` 是 final 方法，无法重写。

**修复**：使用 try-finally 块。

### ❌ 错误3：忘记检查 isStopped

```kotlin
while (true) {
    doWork()
    delay(1000)  // delay 会响应取消
    // 但如果 doWork() 耗时很长，无法及时停止
}
```

**修复**：
```kotlin
while (!isStopped) {
    doWork()
    if (isStopped) break
    delay(1000)
}
```

## 最佳实践

### 1. 分层异常处理

```kotlin
override suspend fun doWork(): Result {
    return try {
        // 外层：捕获所有异常
        try {
            // 内层：工作逻辑
            doActualWork()
            Result.success()
        } catch (e: CancellationException) {
            // 取消处理
            throw e
        } finally {
            // 清理逻辑
        }
    } catch (e: CancellationException) {
        throw e  // 必须重新抛出
    } catch (e: Exception) {
        Result.failure()
    }
}
```

### 2. 使用 ensureActive()

```kotlin
while (shouldContinue()) {
    ensureActive()  // 检查协程是否已取消
    doWork()
    delay(1000)
}
```

**优点**：
- 如果协程已取消，立即抛出 `CancellationException`
- 比手动检查 `isStopped` 更简洁

### 3. 及时响应取消

```kotlin
// ❌ 不好：长时间阻塞
Thread.sleep(60000)

// ✅ 好：可响应取消
delay(60000)

// ✅ 更好：分段等待
repeat(60) {
    if (isStopped) return@repeat
    delay(1000)
}
```

## 调试技巧

### 1. 启用详细日志

```kotlin
private fun log(message: String) {
    val threadName = Thread.currentThread().name
    val isCancelled = runBlocking { currentCoroutineContext().isActive.not() }
    Log.d(TAG, "[$threadName] [isStopped=$isStopped] [cancelled=$isCancelled] $message")
}
```

### 2. 监控 Worker 生命周期

```bash
# 过滤 WorkManager 日志
adb logcat -s WM-WorkerWrapper WM-WorkSpec ContinuousLocationWorker
```

### 3. 强制取消测试

```kotlin
// 在测试代码中
val workManager = WorkManager.getInstance(context)

// 启动 Worker
workManager.enqueueUniqueWork(...)

// 3秒后强制取消
delay(3000)
workManager.cancelUniqueWork("continuous_location_tracking")

// 检查最终状态
val workInfo = workManager.getWorkInfosForUniqueWork("...").await()
assert(workInfo[0].state == WorkInfo.State.CANCELLED)
```

## 参考资源

- [Kotlin 协程取消与超时](https://kotlinlang.org/docs/cancellation-and-timeouts.html)
- [WorkManager CoroutineWorker 文档](https://developer.android.com/reference/androidx/work/CoroutineWorker)
- [Structured Concurrency](https://kotlinlang.org/docs/coroutines-basics.html#structured-concurrency)

---

**总结**: 使用 try-finally 和正确处理 CancellationException，而不是尝试重写 final 方法。
