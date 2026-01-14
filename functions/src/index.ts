import * as admin from "firebase-admin";
import {onDocumentCreated} from "firebase-functions/v2/firestore";
import {onSchedule} from "firebase-functions/v2/scheduler";

admin.initializeApp();

/**
 * 请求数据接口
 */
interface LocationRequestData {
  requesterUid: string;
  targetUid: string;
  type: string;
  message?: string;
  phoneNumber?: string;
  playSound?: boolean;
}

/**
 * 根据请求类型构建 FCM Data Message
 * @param {LocationRequestData} requestData - 请求数据
 * @return {Record<string, string>} FCM Data Message
 */
function buildFCMMessage(
  requestData: LocationRequestData,
): Record<string, string> {
  const {type, requesterUid, targetUid} = requestData;

  switch (type) {
  case "single":
    // 单次位置请求
    return {
      type: "LOCATION_REQUEST",
      requesterUid: requesterUid,
      targetUid: targetUid,
    };

  case "continuous":
    // 开始短时实时追踪（60秒）
    return {
      type: "LOCATION_TRACK_START",
      requesterUid: requesterUid,
      targetUid: targetUid,
      duration: "60",
    };

  case "stop_continuous":
    // 停止实时追踪
    return {
      type: "LOCATION_TRACK_STOP",
      requesterUid: requesterUid,
      targetUid: targetUid,
    };

  case "play_sound":
    // 播放查找提示音
    return {
      type: "PLAY_SOUND",
      requesterUid: requesterUid,
      targetUid: targetUid,
    };

  case "stop_sound":
    // 停止播放提示音
    return {
      type: "STOP_SOUND",
      requesterUid: requesterUid,
      targetUid: targetUid,
    };

  case "enable_lost_mode":
    // 启用丢失模式
    return {
      type: "ENABLE_LOST_MODE",
      requesterUid: requesterUid,
      targetUid: targetUid,
      message: requestData.message || "此设备已丢失",
      phoneNumber: requestData.phoneNumber || "",
      playSound: String(requestData.playSound ?? true),
    };

  case "disable_lost_mode":
    // 关闭丢失模式
    return {
      type: "DISABLE_LOST_MODE",
      requesterUid: requesterUid,
      targetUid: targetUid,
    };

  default:
    throw new Error(`不支持的请求类型: ${type}`);
  }
}

/**
 * 清理无效的 FCM Token
 * 只清理永久性错误的 Token（如已卸载、Token失效）
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
          `清理无效 Token: ${fcmTokens[idx]}, 错误: ${errorCode}`,
        );
      } else {
        // 临时性错误，记录但不删除
        console.warn(
          `Token 暂时失败: ${fcmTokens[idx]}, 错误: ${errorCode}`,
        );
      }
    }
  });

  // 从用户文档中移除无效 Token
  if (tokensToRemove.length > 0) {
    console.log(`🗑️ 清理 ${tokensToRemove.length} 个无效 Token`);
    await admin.firestore()
      .collection("users")
      .doc(targetUid)
      .update({
        fcmTokens: admin.firestore.FieldValue
          .arrayRemove(...tokensToRemove),
      });
  }
}

/**
 * 监听 locationRequests 集合的新文档创建事件
 * 支持以下请求类型：
 *
 * 位置追踪：
 * - single: 单次位置更新
 * - continuous: 开始短时实时追踪（60秒）
 * - stop_continuous: 停止实时追踪
 *
 * 声音查找：
 * - play_sound: 播放查找提示音
 * - stop_sound: 停止播放提示音
 *
 * 丢失模式：
 * - enable_lost_mode: 启用丢失模式（含 message, phoneNumber, playSound）
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
      console.log("No data associated with the event");
      return;
    }

    const requestData = snapshot.data();
    const {requesterUid, targetUid, type} = requestData;

    // 验证必需字段
    if (!requesterUid || !targetUid || !type) {
      console.error("❌ 无效的请求数据，缺少必需字段", requestData);
      await snapshot.ref.update({
        status: "failed",
        error: "Missing required fields",
      });
      return;
    }

    console.log(
      `📬 收到位置请求: ${requesterUid} -> ${targetUid}, 类型: ${type}`,
    );

    try {
      // 1. 获取目标用户的 FCM Tokens
      const userDoc = await admin.firestore()
        .collection("users")
        .doc(targetUid)
        .get();

      if (!userDoc.exists) {
        throw new Error(`目标用户不存在: ${targetUid}`);
      }

      const userData = userDoc.data();
      const fcmTokens: string[] = userData?.fcmTokens || [];

      if (fcmTokens.length === 0) {
        throw new Error(`目标用户没有注册的设备: ${targetUid}`);
      }

      console.log(`🎯 找到 ${fcmTokens.length} 个设备，准备发送 FCM 消息`);

      // 2. 根据请求类型构建 FCM 消息
      const messageData = buildFCMMessage(requestData as LocationRequestData);

      // 3. 发送 FCM Data Message
      const message = {
        tokens: fcmTokens,
        data: messageData,
        android: {
          priority: "high" as const,
        },
      };

      const response = await admin.messaging().sendEachForMulticast(message);

      console.log(
        `✅ FCM 发送完成: 成功 ${response.successCount}, 失败 ${response.failureCount}`,
      );

      // 4. 更新请求状态
      await snapshot.ref.update({
        status: "sent",
        successCount: response.successCount,
        failureCount: response.failureCount,
        sentAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      // 5. 清理无效的 FCM Token
      if (response.failureCount > 0) {
        await cleanupInvalidTokens(targetUid, fcmTokens, response.responses);
      }
    } catch (error) {
      console.error("❌ 处理位置请求失败:", error);
      await snapshot.ref.update({
        status: "failed",
        error: String(error),
      });
    }
  },
);

/**
 * 定期清理过期的位置请求记录 (每天执行一次)
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

    const batch = admin.firestore().batch();
    snapshot.docs.forEach((doc) => {
      batch.delete(doc.ref);
    });

    await batch.commit();
    console.log(`清理了 ${snapshot.size} 条过期位置请求记录`);
  },
);
