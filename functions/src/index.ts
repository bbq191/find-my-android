import * as admin from "firebase-admin";
import {onDocumentCreated} from "firebase-functions/v2/firestore";
import {onSchedule} from "firebase-functions/v2/scheduler";

admin.initializeApp();

/**
 * 监听 locationRequests 集合的新文档创建事件
 * 当用户请求位置更新时，发送 FCM Data Message 给目标用户
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
    const {requesterUid, targetUid, timestamp} = requestData;

    console.log(
      `收到位置请求: ${requesterUid} -> ${targetUid}`,
    );

    try {
      // 1. 获取目标用户的 FCM Tokens
      const userDoc = await admin.firestore()
        .collection("users")
        .doc(targetUid)
        .get();

      if (!userDoc.exists) {
        console.error(`目标用户不存在: ${targetUid}`);
        return;
      }

      const userData = userDoc.data();
      const fcmTokens: string[] = userData?.fcmTokens || [];

      if (fcmTokens.length === 0) {
        console.warn(`目标用户没有 FCM Token: ${targetUid}`);
        // 更新请求状态为失败
        await snapshot.ref.update({
          status: "failed",
          error: "No FCM tokens available",
        });
        return;
      }

      // 2. 构建 FCM Data Message
      const message = {
        tokens: fcmTokens,
        data: {
          type: "LOCATION_REQUEST",
          requesterUid: requesterUid,
          timestamp: timestamp.toString(),
        },
        android: {
          priority: "high" as const,
        },
      };

      // 3. 发送 FCM
      const response = await admin.messaging().sendEachForMulticast(message);

      console.log(
        `FCM 发送: ${response.successCount}/${fcmTokens.length}`,
      );

      // 4. 更新请求状态
      await snapshot.ref.update({
        status: "sent",
        successCount: response.successCount,
        failureCount: response.failureCount,
        sentAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      // 5. 清理失败的 Token
      if (response.failureCount > 0) {
        const tokensToRemove: string[] = [];
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            tokensToRemove.push(fcmTokens[idx]);
            const errorMsg = resp.error?.message || "Unknown";
            console.warn(
              `Token 失败: ${fcmTokens[idx]}, 错误: ${errorMsg}`,
            );
          }
        });

        // 从用户文档中移除无效 Token
        if (tokensToRemove.length > 0) {
          await admin.firestore()
            .collection("users")
            .doc(targetUid)
            .update({
              fcmTokens: admin.firestore.FieldValue
                .arrayRemove(...tokensToRemove),
            });
        }
      }
    } catch (error) {
      console.error("发送 FCM 失败:", error);
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
