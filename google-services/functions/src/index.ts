import * as admin from "firebase-admin";
import {onDocumentCreated} from "firebase-functions/v2/firestore";
import {onSchedule} from "firebase-functions/v2/scheduler";

admin.initializeApp();

// ============================================================================
// 类型定义
// ============================================================================

/**
 * 请求类型枚举
 */
const RequestType = {
  // 位置追踪
  SINGLE: "single",
  CONTINUOUS: "continuous",
  STOP_CONTINUOUS: "stop_continuous",
  // 声音查找
  PLAY_SOUND: "play_sound",
  STOP_SOUND: "stop_sound",
  // 丢失模式
  ENABLE_LOST_MODE: "enable_lost_mode",
  DISABLE_LOST_MODE: "disable_lost_mode",
} as const;

type RequestTypeValue = typeof RequestType[keyof typeof RequestType];

/**
 * 基础请求数据
 */
interface BaseRequestData {
  requesterUid: string;
  targetUid: string;
  type: RequestTypeValue;
  timestamp: number;
  status: string;
}

/**
 * 丢失模式请求数据
 */
interface LostModeRequestData extends BaseRequestData {
  type: typeof RequestType.ENABLE_LOST_MODE;
  message?: string;
  phoneNumber?: string;
  playSound?: boolean;
}

/**
 * 通用请求数据
 */
type LocationRequestData = BaseRequestData | LostModeRequestData;

// ============================================================================
// 安全验证
// ============================================================================

/**
 * 验证请求者是否有权向目标用户发送请求
 * 检查两者之间是否存在有效的共享关系
 *
 * @param {string} requesterUid - 请求者 UID
 * @param {string} targetUid - 目标用户 UID
 * @return {Promise<boolean>} 是否有权限
 */
async function validateSharingRelationship(
  requesterUid: string,
  targetUid: string,
): Promise<boolean> {
  try {
    // 查询 contacts 集合，检查是否存在有效的共享关系
    // 条件：requesterUid 的联系人中包含 targetUid，且状态为 accepted
    const contactsSnapshot = await admin.firestore()
      .collection("contacts")
      .where("ownerUserId", "==", requesterUid)
      .where("targetUserId", "==", targetUid)
      .where("shareStatus", "==", "accepted")
      .limit(1)
      .get();

    if (!contactsSnapshot.empty) {
      return true;
    }

    // 反向检查：targetUid 是否共享给了 requesterUid
    const reverseSnapshot = await admin.firestore()
      .collection("contacts")
      .where("ownerUserId", "==", targetUid)
      .where("targetUserId", "==", requesterUid)
      .where("shareStatus", "==", "accepted")
      .limit(1)
      .get();

    return !reverseSnapshot.empty;
  } catch (error) {
    console.error("验证共享关系失败:", error);
    return false;
  }
}

/**
 * 验证请求者是否为目标设备的所有者
 * 用于验证对自己设备的操作（如响铃、丢失模式）
 *
 * @param {string} requesterUid - 请求者 UID
 * @param {string} targetUid - 目标用户 UID
 * @return {boolean} 是否为同一用户
 */
function isSameUser(requesterUid: string, targetUid: string): boolean {
  return requesterUid === targetUid;
}

// ============================================================================
// 频率限制 & 去重
// ============================================================================

/**
 * 频率限制配置（每种请求类型的限制）
 */
const RATE_LIMITS: Record<string, { maxRequests: number; windowMs: number }> = {
  [RequestType.SINGLE]: {maxRequests: 10, windowMs: 60000}, // 每分钟 10 次
  [RequestType.CONTINUOUS]: {maxRequests: 5, windowMs: 60000}, // 每分钟 5 次
  [RequestType.PLAY_SOUND]: {maxRequests: 3, windowMs: 60000}, // 每分钟 3 次
  [RequestType.ENABLE_LOST_MODE]: {maxRequests: 2, windowMs: 60000}, // 每分钟 2 次
  default: {maxRequests: 10, windowMs: 60000},
};

/**
 * 去重时间窗口（毫秒）
 * 相同的请求在此时间内只处理一次
 */
const DEDUP_WINDOW_MS = 5000; // 5 秒

/**
 * 检查是否为重复请求
 *
 * @param {string} requesterUid - 请求者 UID
 * @param {string} targetUid - 目标用户 UID
 * @param {string} type - 请求类型
 * @return {Promise<boolean>} 是否为重复请求
 */
async function isDuplicateRequest(
  requesterUid: string,
  targetUid: string,
  type: string,
): Promise<boolean> {
  const cutoffTime = Date.now() - DEDUP_WINDOW_MS;

  const duplicateSnapshot = await admin.firestore()
    .collection("locationRequests")
    .where("requesterUid", "==", requesterUid)
    .where("targetUid", "==", targetUid)
    .where("type", "==", type)
    .where("timestamp", ">", cutoffTime)
    .where("status", "in", ["pending", "sent"])
    .limit(2) // 查找 2 条，因为当前请求也会被计入
    .get();

  // 如果找到超过 1 条（当前请求 + 之前的请求），则为重复
  return duplicateSnapshot.size > 1;
}

/**
 * 检查是否超过频率限制
 *
 * @param {string} requesterUid - 请求者 UID
 * @param {string} type - 请求类型
 * @return {Promise<boolean>} 是否超过限制
 */
async function isRateLimited(
  requesterUid: string,
  type: string,
): Promise<boolean> {
  const limit = RATE_LIMITS[type] || RATE_LIMITS.default;
  const cutoffTime = Date.now() - limit.windowMs;

  const recentRequests = await admin.firestore()
    .collection("locationRequests")
    .where("requesterUid", "==", requesterUid)
    .where("type", "==", type)
    .where("timestamp", ">", cutoffTime)
    .count()
    .get();

  return recentRequests.data().count >= limit.maxRequests;
}

// ============================================================================
// FCM 消息构建
// ============================================================================

/**
 * 根据请求类型构建 FCM Data Message
 *
 * @param {LocationRequestData} requestData - 请求数据
 * @return {Record<string, string>} FCM Data Message
 */
function buildFCMMessage(
  requestData: LocationRequestData,
): Record<string, string> {
  const {type, requesterUid, targetUid} = requestData;

  switch (type) {
  case RequestType.SINGLE:
    return {
      type: "LOCATION_REQUEST",
      requesterUid,
      targetUid,
    };

  case RequestType.CONTINUOUS:
    return {
      type: "LOCATION_TRACK_START",
      requesterUid,
      targetUid,
      duration: "60",
    };

  case RequestType.STOP_CONTINUOUS:
    return {
      type: "LOCATION_TRACK_STOP",
      requesterUid,
      targetUid,
    };

  case RequestType.PLAY_SOUND:
    return {
      type: "PLAY_SOUND",
      requesterUid,
      targetUid,
    };

  case RequestType.STOP_SOUND:
    return {
      type: "STOP_SOUND",
      requesterUid,
      targetUid,
    };

  case RequestType.ENABLE_LOST_MODE: {
    const lostModeData = requestData as LostModeRequestData;
    return {
      type: "ENABLE_LOST_MODE",
      requesterUid,
      targetUid,
      message: lostModeData.message || "此设备已丢失",
      phoneNumber: lostModeData.phoneNumber || "",
      playSound: String(lostModeData.playSound ?? true),
    };
  }

  case RequestType.DISABLE_LOST_MODE:
    return {
      type: "DISABLE_LOST_MODE",
      requesterUid,
      targetUid,
    };

  default:
    throw new Error(`不支持的请求类型: ${type}`);
  }
}

// ============================================================================
// FCM Token 清理
// ============================================================================

/**
 * 清理无效的 FCM Token
 * 只清理永久性错误的 Token（如已卸载、Token失效）
 *
 * @param {string} targetUid - 目标用户 UID
 * @param {string[]} fcmTokens - FCM Token 列表
 * @param {admin.messaging.SendResponse[]} responses - FCM 发送响应列表
 * @return {Promise<void>} Promise
 */
async function cleanupInvalidTokens(
  targetUid: string,
  fcmTokens: string[],
  responses: admin.messaging.SendResponse[],
): Promise<void> {
  const tokensToRemove: string[] = [];

  responses.forEach((resp, idx) => {
    if (!resp.success && resp.error) {
      const errorCode = resp.error.code;
      // 只清理永久性错误
      if (
        errorCode === "messaging/invalid-registration-token" ||
        errorCode === "messaging/registration-token-not-registered"
      ) {
        tokensToRemove.push(fcmTokens[idx]);
        console.warn(
          `[Token清理] 移除无效 Token: ${fcmTokens[idx].slice(0, 20)}...`,
        );
      } else {
        console.warn(
          `[Token警告] 暂时失败: ${errorCode}`,
        );
      }
    }
  });

  if (tokensToRemove.length > 0) {
    console.log(`[Token清理] 清理 ${tokensToRemove.length} 个无效 Token`);
    await admin.firestore()
      .collection("users")
      .doc(targetUid)
      .update({
        fcmTokens: admin.firestore.FieldValue.arrayRemove(...tokensToRemove),
      });
  }
}

// ============================================================================
// 主函数：处理位置请求
// ============================================================================

/**
 * 监听 locationRequests 集合的新文档创建事件
 *
 * 安全机制：
 * 1. 验证共享关系（或设备所有权）
 * 2. 请求去重（5秒内相同请求只处理一次）
 * 3. 频率限制（防止滥用）
 *
 * 支持以下请求类型：
 * - single: 单次位置更新
 * - continuous: 开始短时实时追踪（60秒）
 * - stop_continuous: 停止实时追踪
 * - play_sound: 播放查找提示音
 * - stop_sound: 停止播放提示音
 * - enable_lost_mode: 启用丢失模式
 * - disable_lost_mode: 关闭丢失模式
 */
export const onLocationRequest = onDocumentCreated(
  {
    document: "locationRequests/{requestId}",
    region: "asia-northeast1",
  },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
      console.log("[请求处理] 无数据");
      return;
    }

    const requestData = snapshot.data() as LocationRequestData;
    const {requesterUid, targetUid, type} = requestData;

    // 1. 验证必需字段
    if (!requesterUid || !targetUid || !type) {
      console.error("[安全] 请求数据不完整", {requesterUid, targetUid, type});
      await snapshot.ref.update({
        status: "failed",
        error: "INVALID_REQUEST: 缺少必需字段",
      });
      return;
    }

    console.log(`[请求] ${type}: ${requesterUid} -> ${targetUid}`);

    try {
      // 2. 安全验证：检查权限
      const hasPermission = isSameUser(requesterUid, targetUid) ||
        await validateSharingRelationship(requesterUid, targetUid);

      if (!hasPermission) {
        console.warn(`[安全] 权限拒绝: ${requesterUid} 无权访问 ${targetUid}`);
        await snapshot.ref.update({
          status: "failed",
          error: "PERMISSION_DENIED: 无共享关系",
        });
        return;
      }

      // 3. 去重检查
      if (await isDuplicateRequest(requesterUid, targetUid, type)) {
        console.log(`[去重] 跳过重复请求: ${type}`);
        await snapshot.ref.update({
          status: "skipped",
          error: "DUPLICATE_REQUEST: 请求已存在",
        });
        return;
      }

      // 4. 频率限制检查
      if (await isRateLimited(requesterUid, type)) {
        console.warn(`[限流] 请求过于频繁: ${requesterUid}, ${type}`);
        await snapshot.ref.update({
          status: "failed",
          error: "RATE_LIMITED: 请求过于频繁，请稍后重试",
        });
        return;
      }

      // 5. 获取目标用户的 FCM Tokens
      const userDoc = await admin.firestore()
        .collection("users")
        .doc(targetUid)
        .get();

      if (!userDoc.exists) {
        throw new Error("USER_NOT_FOUND: 目标用户不存在");
      }

      const userData = userDoc.data();
      const fcmTokens: string[] = userData?.fcmTokens || [];

      if (fcmTokens.length === 0) {
        throw new Error("NO_DEVICE: 目标用户没有注册的设备");
      }

      console.log(`[FCM] 发送到 ${fcmTokens.length} 个设备`);

      // 6. 构建并发送 FCM 消息
      const messageData = buildFCMMessage(requestData);
      const message = {
        tokens: fcmTokens,
        data: messageData,
        android: {
          priority: "high" as const,
        },
      };

      const response = await admin.messaging().sendEachForMulticast(message);

      console.log(
        `[FCM] 完成: 成功=${response.successCount}, 失败=${response.failureCount}`,
      );

      // 7. 更新请求状态
      await snapshot.ref.update({
        status: "sent",
        successCount: response.successCount,
        failureCount: response.failureCount,
        sentAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      // 8. 清理无效的 FCM Token
      if (response.failureCount > 0) {
        await cleanupInvalidTokens(targetUid, fcmTokens, response.responses);
      }
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : String(error);
      console.error(`[错误] ${errorMessage}`);
      await snapshot.ref.update({
        status: "failed",
        error: errorMessage,
      });
    }
  },
);

// ============================================================================
// 定时任务：清理过期记录
// ============================================================================

/**
 * 定期清理过期的位置请求记录
 * 每天执行一次，清理超过 24 小时的记录
 */
export const cleanupOldLocationRequests = onSchedule(
  {
    schedule: "every 24 hours",
    region: "asia-northeast1",
  },
  async () => {
    const oneDayAgo = Date.now() - 24 * 60 * 60 * 1000;

    const snapshot = await admin.firestore()
      .collection("locationRequests")
      .where("timestamp", "<", oneDayAgo)
      .get();

    if (snapshot.empty) {
      console.log("[清理] 无过期记录");
      return;
    }

    const batch = admin.firestore().batch();
    snapshot.docs.forEach((doc) => {
      batch.delete(doc.ref);
    });

    await batch.commit();
    console.log(`[清理] 已清理 ${snapshot.size} 条过期记录`);
  },
);
