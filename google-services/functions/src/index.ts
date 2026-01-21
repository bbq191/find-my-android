import * as admin from "firebase-admin";
import {onRequest, onCall, HttpsError} from "firebase-functions/v2/https";
import {onSchedule} from "firebase-functions/v2/scheduler";

admin.initializeApp();

// Firestore 实例
const db = admin.firestore();

// ============================================================================
// Firestore 集合名称
// ============================================================================

const COLLECTIONS = {
  // FCM Token 存储集合 (文档 ID: deviceId)
  FCM_TOKENS: "fcm_tokens",
  // UID 到 deviceId 的映射集合 (文档 ID: uid)
  UID_DEVICE_MAP: "uid_device_map",
} as const;

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
  HEARTBEAT: "heartbeat",
  // 声音查找
  PLAY_SOUND: "play_sound",
  STOP_SOUND: "stop_sound",
  // 丢失模式
  ENABLE_LOST_MODE: "enable_lost_mode",
  DISABLE_LOST_MODE: "disable_lost_mode",
} as const;

type RequestTypeValue = typeof RequestType[keyof typeof RequestType];

/**
 * 推送请求数据
 * 支持通过 targetUid 或 targetToken 发送推送
 */
interface PushRequest {
  // 目标用户的 UID（优先使用，服务端根据此查找 FCM Token）
  targetUid?: string;
  // 目标设备的 FCM Token（向后兼容）
  targetToken?: string;
  type: RequestTypeValue;
  requesterId?: string;
  // 丢失模式参数
  message?: string;
  phoneNumber?: string;
  playSound?: boolean;
}

/**
 * Token 注册请求
 * 包含用户 UID，用于建立 UID -> Token 的映射
 */
interface TokenRegistrationRequest {
  deviceId: string;
  fcmToken: string;
  // 用户 UID，用于通过 UID 查找 Token
  uid?: string;
  platform?: string;
  appVersion?: string;
}

/**
 * 错误代码常量
 */
const ErrorCode = {
  // 目标用户未注册 Token
  TOKEN_NOT_REGISTERED: "TOKEN_NOT_REGISTERED",
  // FCM Token 无效或过期
  TOKEN_INVALID: "TOKEN_INVALID",
  // 目标用户不存在
  USER_NOT_FOUND: "USER_NOT_FOUND",
  // 发送失败
  SEND_FAILED: "SEND_FAILED",
} as const;

/**
 * Firestore Token 文档结构 (存储在 fcm_tokens 集合)
 */
interface TokenDocument {
  // FCM Token
  token: string;
  // 更新时间戳
  updatedAt: admin.firestore.Timestamp;
  // 平台 (android/ios)
  platform?: string;
  // 关联的用户 UID
  uid?: string;
  // 设备 ID (冗余存储，方便查询)
  deviceId: string;
}

/**
 * Firestore UID 映射文档结构 (存储在 uid_device_map 集合)
 */
interface UidDeviceDocument {
  // 关联的设备 ID
  deviceId: string;
  // 更新时间戳
  updatedAt: admin.firestore.Timestamp;
}

// ============================================================================
// 频率限制
// ============================================================================

/**
 * 频率限制配置
 */
const RATE_LIMITS: Record<string, { maxRequests: number; windowMs: number }> = {
  [RequestType.SINGLE]: {maxRequests: 30, windowMs: 60000},
  [RequestType.CONTINUOUS]: {maxRequests: 10, windowMs: 60000},
  [RequestType.PLAY_SOUND]: {maxRequests: 5, windowMs: 60000},
  [RequestType.ENABLE_LOST_MODE]: {maxRequests: 3, windowMs: 60000},
  default: {maxRequests: 30, windowMs: 60000},
};

/**
 * 请求计数器（内存存储，生产环境应使用 Redis）
 */
const requestCounts = new Map<string, { count: number; resetAt: number }>();

/**
 * 检查频率限制
 * @param {string} clientId - 客户端标识
 * @param {string} type - 请求类型
 * @return {boolean} 是否允许请求
 */
function checkRateLimit(clientId: string, type: string): boolean {
  const limit = RATE_LIMITS[type] || RATE_LIMITS.default;
  const key = `${clientId}:${type}`;
  const now = Date.now();

  const record = requestCounts.get(key);
  if (!record || now > record.resetAt) {
    requestCounts.set(key, {count: 1, resetAt: now + limit.windowMs});
    return true;
  }

  if (record.count >= limit.maxRequests) {
    return false;
  }

  record.count++;
  return true;
}

// ============================================================================
// FCM 消息构建
// ============================================================================

/**
 * 构建 FCM Data Message
 * @param {PushRequest} request - 推送请求
 * @return {Record<string, string>} FCM 数据消息
 */
function buildFCMDataMessage(request: PushRequest): Record<string, string> {
  const base: Record<string, string> = {
    type: request.type,
    requesterId: request.requesterId || "",
    timestamp: String(Date.now()),
  };

  switch (request.type) {
  case RequestType.SINGLE:
    return {
      ...base,
      type: "LOCATION_REQUEST",
      mode: "single",
    };

  case RequestType.CONTINUOUS:
    return {
      ...base,
      type: "LOCATION_REQUEST",
      mode: "continuous",
    };

  case RequestType.STOP_CONTINUOUS:
    return {
      ...base,
      type: "LOCATION_REQUEST",
      mode: "stop",
    };

  case RequestType.HEARTBEAT:
    return {
      ...base,
      type: "LOCATION_REQUEST",
      mode: "heartbeat",
    };

  case RequestType.PLAY_SOUND:
    return {
      ...base,
      type: "DEVICE_COMMAND",
      command: "PLAY_SOUND",
    };

  case RequestType.STOP_SOUND:
    return {
      ...base,
      type: "DEVICE_COMMAND",
      command: "STOP_SOUND",
    };

  case RequestType.ENABLE_LOST_MODE:
    return {
      ...base,
      type: "DEVICE_COMMAND",
      command: "LOST_MODE",
      message: request.message || "此设备已丢失",
      phoneNumber: request.phoneNumber || "",
      playSound: String(request.playSound ?? true),
    };

  case RequestType.DISABLE_LOST_MODE:
    return {
      ...base,
      type: "DEVICE_COMMAND",
      command: "DISABLE_LOST_MODE",
    };

  default:
    return base;
  }
}

// ============================================================================
// HTTP API: FCM Token 注册
// ============================================================================

/**
 * 注册 FCM Token
 * POST /registerToken
 *
 * Body: {
 *   deviceId: string,
 *   fcmToken: string,
 *   uid?: string,      // 用户 UID，用于建立 UID -> Token 映射
 *   platform?: string,
 *   appVersion?: string
 * }
 */
export const registerToken = onRequest(
  {
    region: "asia-northeast1",
    cors: true,
  },
  async (req, res) => {
    // 只允许 POST
    if (req.method !== "POST") {
      res.status(405).json({error: "Method not allowed"});
      return;
    }

    try {
      const data = req.body as TokenRegistrationRequest;

      // 验证必需字段
      if (!data.deviceId || !data.fcmToken) {
        res.status(400).json({
          error: "Missing required fields: deviceId, fcmToken",
        });
        return;
      }

      const now = admin.firestore.Timestamp.now();
      const batch = db.batch();

      // 存储 Token 到 Firestore (文档 ID 使用 deviceId)
      const tokenDocRef = db.collection(COLLECTIONS.FCM_TOKENS)
        .doc(data.deviceId);
      const tokenData: TokenDocument = {
        token: data.fcmToken,
        updatedAt: now,
        platform: data.platform,
        uid: data.uid,
        deviceId: data.deviceId,
      };
      batch.set(tokenDocRef, tokenData, {merge: true});

      // 如果提供了 UID，建立 UID -> deviceId 映射
      if (data.uid) {
        const uidDocRef = db.collection(COLLECTIONS.UID_DEVICE_MAP)
          .doc(data.uid);
        const uidData: UidDeviceDocument = {
          deviceId: data.deviceId,
          updatedAt: now,
        };
        batch.set(uidDocRef, uidData, {merge: true});
      }

      // 提交批量写入
      await batch.commit();

      if (data.uid) {
        console.log(
          `[Token] 注册成功: uid=${data.uid.slice(0, 8)}..., ` +
          `device=${data.deviceId.slice(0, 8)}...`
        );
      } else {
        console.log(`[Token] 注册成功: device=${data.deviceId.slice(0, 8)}...`);
      }

      res.status(200).json({
        success: true,
        message: "Token registered successfully",
      });
    } catch (error) {
      console.error("[Token] 注册失败:", error);
      res.status(500).json({
        error: "Internal server error",
      });
    }
  },
);

// ============================================================================
// HTTP API: 发送推送通知
// ============================================================================

/**
 * Token 解析结果
 */
interface TokenResolveResult {
  fcmToken: string | null;
  error: string | null;
  code: string | null;
}

/**
 * 通过 UID 或 deviceId 查找 FCM Token (异步版本，从 Firestore 读取)
 * @param {string} targetUid - 目标用户 UID
 * @param {string} targetToken - 目标设备 ID 或 FCM Token
 * @return {Promise<TokenResolveResult>} 包含 fcmToken, error, code 的对象
 */
async function resolveFcmToken(
  targetUid?: string,
  targetToken?: string
): Promise<TokenResolveResult> {
  // 优先使用 targetUid 查找
  if (targetUid) {
    // 从 Firestore 查找 UID -> deviceId 映射
    const uidDoc = await db.collection(COLLECTIONS.UID_DEVICE_MAP)
      .doc(targetUid)
      .get();

    if (!uidDoc.exists) {
      return {
        fcmToken: null,
        error: `User ${targetUid.slice(0, 8)}... has no registered device`,
        code: ErrorCode.TOKEN_NOT_REGISTERED,
      };
    }

    const uidData = uidDoc.data() as UidDeviceDocument;
    const deviceId = uidData.deviceId;

    // 从 Firestore 查找 deviceId -> Token
    const tokenDoc = await db.collection(COLLECTIONS.FCM_TOKENS)
      .doc(deviceId)
      .get();

    if (!tokenDoc.exists) {
      return {
        fcmToken: null,
        error: `Device token not found for user ${targetUid.slice(0, 8)}...`,
        code: ErrorCode.TOKEN_NOT_REGISTERED,
      };
    }

    const tokenData = tokenDoc.data() as TokenDocument;
    return {fcmToken: tokenData.token, error: null, code: null};
  }

  // 使用 targetToken（向后兼容）
  if (targetToken) {
    // 如果是完整的 FCM Token（包含冒号）
    if (targetToken.includes(":")) {
      return {fcmToken: targetToken, error: null, code: null};
    }

    // 尝试作为 deviceId 查找
    const tokenDoc = await db.collection(COLLECTIONS.FCM_TOKENS)
      .doc(targetToken)
      .get();

    if (tokenDoc.exists) {
      const tokenData = tokenDoc.data() as TokenDocument;
      return {fcmToken: tokenData.token, error: null, code: null};
    }

    // 尝试作为 UID 查找（兼容旧版客户端）
    const uidDoc = await db.collection(COLLECTIONS.UID_DEVICE_MAP)
      .doc(targetToken)
      .get();

    if (uidDoc.exists) {
      const uidData = uidDoc.data() as UidDeviceDocument;
      const tokenDocByUid = await db.collection(COLLECTIONS.FCM_TOKENS)
        .doc(uidData.deviceId)
        .get();

      if (tokenDocByUid.exists) {
        const tokenData = tokenDocByUid.data() as TokenDocument;
        return {fcmToken: tokenData.token, error: null, code: null};
      }
    }

    return {
      fcmToken: null,
      error: `Token not found for: ${targetToken.slice(0, 8)}...`,
      code: ErrorCode.TOKEN_NOT_REGISTERED,
    };
  }

  return {
    fcmToken: null,
    error: "No target specified",
    code: ErrorCode.USER_NOT_FOUND,
  };
}

/**
 * 发送推送通知
 * POST /sendPush
 *
 * Body: {
 *   targetUid?: string,   // 目标用户 UID（优先使用）
 *   targetToken?: string, // FCM Token 或设备 ID（向后兼容）
 *   type: string,         // 请求类型
 *   requesterId?: string,
 *   message?: string,     // 丢失模式消息
 *   phoneNumber?: string, // 丢失模式电话
 *   playSound?: boolean   // 是否播放声音
 * }
 */
export const sendPush = onRequest(
  {
    region: "asia-northeast1",
    cors: true,
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({error: "Method not allowed"});
      return;
    }

    try {
      const data = req.body as PushRequest;

      // 验证必需字段：需要 targetUid 或 targetToken 其中之一
      if (!data.targetUid && !data.targetToken) {
        res.status(400).json({
          error: "Missing required fields: targetUid or targetToken",
          code: ErrorCode.USER_NOT_FOUND,
        });
        return;
      }

      if (!data.type) {
        res.status(400).json({
          error: "Missing required field: type",
        });
        return;
      }

      // 验证请求类型
      const validTypes = Object.values(RequestType);
      if (!validTypes.includes(data.type as RequestTypeValue)) {
        res.status(400).json({
          error: `Invalid type. Valid types: ${validTypes.join(", ")}`,
        });
        return;
      }

      // 频率限制检查
      const clientId = req.ip || data.requesterId || "unknown";
      if (!checkRateLimit(clientId, data.type)) {
        res.status(429).json({
          error: "Rate limit exceeded. Please try again later.",
        });
        return;
      }

      // 解析目标 FCM Token (从 Firestore 查询)
      const {fcmToken, error, code} = await resolveFcmToken(
        data.targetUid,
        data.targetToken
      );

      if (!fcmToken) {
        console.warn(`[Push] Token 查找失败: ${error}`);
        res.status(404).json({
          success: false,
          error,
          code,
          tokenInvalid: true,
        });
        return;
      }

      // 构建 FCM 消息
      const messageData = buildFCMDataMessage(data);
      const message: admin.messaging.Message = {
        token: fcmToken,
        data: messageData,
        android: {
          priority: "high",
          ttl: 60000, // 1 分钟 TTL
        },
      };

      const targetDesc = data.targetUid ?
        `uid=${data.targetUid.slice(0, 8)}...` :
        `token=${fcmToken.slice(0, 20)}...`;
      console.log(`[Push] 发送 ${data.type} -> ${targetDesc}`);

      // 发送 FCM 消息
      const messageId = await admin.messaging().send(message);

      console.log(`[Push] 成功: ${messageId}`);

      res.status(200).json({
        success: true,
        messageId,
      });
    } catch (error) {
      const errorMessage = error instanceof Error ?
        error.message : String(error);
      console.error("[Push] 失败:", errorMessage);

      // 处理特定 FCM 错误
      if (errorMessage.includes("not-registered") ||
        errorMessage.includes("invalid-registration-token") ||
        errorMessage.includes("not a valid FCM registration token")) {
        res.status(410).json({
          success: false,
          error: "Token is invalid or expired",
          code: ErrorCode.TOKEN_INVALID,
          tokenInvalid: true,
        });
        return;
      }

      res.status(500).json({
        success: false,
        error: "Failed to send push notification",
        code: ErrorCode.SEND_FAILED,
        details: errorMessage,
      });
    }
  },
);

// ============================================================================
// Callable Function: 发送推送（带身份验证）
// ============================================================================

/**
 * 发送推送通知（需要 Firebase Auth）
 * 用于已登录用户之间的推送
 */
export const sendPushAuthenticated = onCall(
  {
    region: "asia-northeast1",
  },
  async (request) => {
    // 验证用户已登录
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "需要登录才能发送推送");
    }

    const data = request.data as PushRequest;
    const requesterUid = request.auth.uid;

    // 验证必需字段：需要 targetUid 或 targetToken 其中之一
    if (!data.targetUid && !data.targetToken) {
      throw new HttpsError(
        "invalid-argument",
        "缺少必需字段: targetUid 或 targetToken"
      );
    }

    if (!data.type) {
      throw new HttpsError("invalid-argument", "缺少必需字段: type");
    }

    // 频率限制
    if (!checkRateLimit(requesterUid, data.type)) {
      throw new HttpsError("resource-exhausted", "请求过于频繁，请稍后重试");
    }

    try {
      // 解析目标 FCM Token (从 Firestore 查询)
      const {fcmToken, error, code} = await resolveFcmToken(
        data.targetUid,
        data.targetToken
      );

      if (!fcmToken) {
        console.warn(`[CallablePush] Token 查找失败: ${error}`);
        throw new HttpsError("not-found", error || "Token not found", {code});
      }

      // 构建并发送消息
      const messageData = buildFCMDataMessage({
        ...data,
        requesterId: requesterUid,
      });

      const message: admin.messaging.Message = {
        token: fcmToken,
        data: messageData,
        android: {
          priority: "high",
        },
      };

      const messageId = await admin.messaging().send(message);

      const targetDesc = data.targetUid ?
        `uid=${data.targetUid.slice(0, 8)}...` :
        `token=${fcmToken.slice(0, 20)}...`;
      console.log(
        `[CallablePush] ${requesterUid} -> ${targetDesc}: ${messageId}`,
      );

      return {success: true, messageId};
    } catch (error) {
      if (error instanceof HttpsError) {
        throw error;
      }
      const errorMessage = error instanceof Error ?
        error.message : String(error);
      console.error("[CallablePush] 失败:", errorMessage);

      // 处理特定 FCM 错误
      if (errorMessage.includes("not-registered") ||
        errorMessage.includes("invalid-registration-token") ||
        errorMessage.includes("not a valid FCM registration token")) {
        throw new HttpsError(
          "failed-precondition",
          "Token is invalid or expired",
          {code: ErrorCode.TOKEN_INVALID, tokenInvalid: true}
        );
      }

      throw new HttpsError("internal", errorMessage);
    }
  },
);

// ============================================================================
// HTTP API: 批量发送推送
// ============================================================================

/**
 * 批量发送推送通知
 * POST /sendPushBatch
 *
 * Body: {
 *   targets: Array<{
 *     targetUid?: string,   // 目标用户 UID
 *     targetToken?: string, // FCM Token 或设备 ID
 *     type: string,
 *     ...
 *   }>
 * }
 */
export const sendPushBatch = onRequest(
  {
    region: "asia-northeast1",
    cors: true,
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({error: "Method not allowed"});
      return;
    }

    try {
      const {targets} = req.body as { targets: PushRequest[] };

      if (!targets || !Array.isArray(targets) || targets.length === 0) {
        res.status(400).json({error: "Missing or empty targets array"});
        return;
      }

      if (targets.length > 500) {
        res.status(400).json({error: "Maximum 500 targets per request"});
        return;
      }

      // 构建消息列表，过滤掉无法解析 Token 的目标
      const messages: admin.messaging.Message[] = [];
      let skippedCount = 0;

      // 并行解析所有目标的 FCM Token
      const resolvePromises = targets.map((target) =>
        resolveFcmToken(target.targetUid, target.targetToken)
      );
      const resolvedTokens = await Promise.all(resolvePromises);

      for (let i = 0; i < targets.length; i++) {
        const resolved = resolvedTokens[i];
        const fcmToken = resolved.fcmToken;

        if (fcmToken) {
          messages.push({
            token: fcmToken,
            data: buildFCMDataMessage(targets[i]),
            android: {
              priority: "high" as const,
            },
          });
        } else {
          skippedCount++;
        }
      }

      // 如果没有有效消息
      if (messages.length === 0) {
        res.status(200).json({
          success: true,
          successCount: 0,
          failureCount: 0,
          skippedCount,
        });
        return;
      }

      // 批量发送
      const response = await admin.messaging().sendEach(messages);

      console.log(
        `[BatchPush] 成功=${response.successCount}, ` +
        `失败=${response.failureCount}, 跳过=${skippedCount}`,
      );

      res.status(200).json({
        success: true,
        successCount: response.successCount,
        failureCount: response.failureCount,
        skippedCount,
      });
    } catch (error) {
      console.error("[BatchPush] 失败:", error);
      res.status(500).json({error: "Batch send failed"});
    }
  },
);

// ============================================================================
// HTTP API: 查询 Token 状态
// ============================================================================

/**
 * 查询设备 Token 是否已注册
 * GET /tokenStatus?deviceId=xxx
 * GET /tokenStatus?uid=xxx
 */
export const tokenStatus = onRequest(
  {
    region: "asia-northeast1",
    cors: true,
  },
  async (req, res) => {
    if (req.method !== "GET") {
      res.status(405).json({error: "Method not allowed"});
      return;
    }

    const deviceId = req.query.deviceId as string;
    const uid = req.query.uid as string;

    if (!deviceId && !uid) {
      res.status(400).json({error: "Missing deviceId or uid parameter"});
      return;
    }

    try {
      let tokenDoc: admin.firestore.DocumentSnapshot | null = null;
      let resolvedDeviceId: string | undefined;

      if (deviceId) {
        // 直接通过 deviceId 查询
        tokenDoc = await db.collection(COLLECTIONS.FCM_TOKENS)
          .doc(deviceId)
          .get();
        resolvedDeviceId = deviceId;
      } else if (uid) {
        // 先通过 UID 查找 deviceId
        const uidDoc = await db.collection(COLLECTIONS.UID_DEVICE_MAP)
          .doc(uid)
          .get();

        if (uidDoc.exists) {
          const uidData = uidDoc.data() as UidDeviceDocument;
          resolvedDeviceId = uidData.deviceId;
          tokenDoc = await db.collection(COLLECTIONS.FCM_TOKENS)
            .doc(resolvedDeviceId)
            .get();
        }
      }

      if (tokenDoc && tokenDoc.exists) {
        const tokenData = tokenDoc.data() as TokenDocument;
        res.status(200).json({
          registered: true,
          deviceId: resolvedDeviceId,
          updatedAt: tokenData.updatedAt.toMillis(),
          platform: tokenData.platform,
          uid: tokenData.uid,
        });
      } else {
        res.status(200).json({
          registered: false,
        });
      }
    } catch (error) {
      console.error("[TokenStatus] 查询失败:", error);
      res.status(500).json({error: "Internal server error"});
    }
  },
);

// ============================================================================
// 定时任务：清理过期 Token
// ============================================================================

/**
 * 定期清理过期的 Token（7天未更新）
 * 同时清理对应的 UID 映射
 */
export const cleanupExpiredTokens = onSchedule(
  {
    schedule: "every 24 hours",
    region: "asia-northeast1",
  },
  async () => {
    const sevenDaysAgo = admin.firestore.Timestamp.fromMillis(
      Date.now() - 7 * 24 * 60 * 60 * 1000
    );

    let cleanedTokenCount = 0;
    let cleanedUidCount = 0;

    // 查询过期的 Token 文档
    const expiredTokensQuery = db.collection(COLLECTIONS.FCM_TOKENS)
      .where("updatedAt", "<", sevenDaysAgo)
      .limit(500); // 每次最多处理 500 条

    const expiredTokens = await expiredTokensQuery.get();

    if (expiredTokens.empty) {
      console.log("[清理] 没有过期的 Token");
      return;
    }

    const batch = db.batch();

    for (const doc of expiredTokens.docs) {
      const tokenData = doc.data() as TokenDocument;

      // 删除 Token 文档
      batch.delete(doc.ref);
      cleanedTokenCount++;

      // 如果有关联的 UID，也删除 UID 映射
      if (tokenData.uid) {
        const uidDocRef = db.collection(COLLECTIONS.UID_DEVICE_MAP)
          .doc(tokenData.uid);
        const uidDoc = await uidDocRef.get();

        // 只有当 UID 映射指向当前设备时才删除
        if (uidDoc.exists) {
          const uidData = uidDoc.data() as UidDeviceDocument;
          if (uidData.deviceId === tokenData.deviceId) {
            batch.delete(uidDocRef);
            cleanedUidCount++;
          }
        }
      }
    }

    await batch.commit();

    console.log(
      `[清理] 已清理 ${cleanedTokenCount} 个过期 Token, ` +
      `${cleanedUidCount} 个 UID 映射`
    );
  },
);

// ============================================================================
// 定时任务：清理频率限制计数器
// ============================================================================

/**
 * 定期清理过期的频率限制记录
 */
export const cleanupRateLimitRecords = onSchedule(
  {
    schedule: "every 1 hours",
    region: "asia-northeast1",
  },
  async () => {
    const now = Date.now();
    let cleanedCount = 0;

    for (const [key, record] of requestCounts.entries()) {
      if (now > record.resetAt) {
        requestCounts.delete(key);
        cleanedCount++;
      }
    }

    console.log(`[清理] 已清理 ${cleanedCount} 条频率限制记录`);
  },
);

// ============================================================================
// 健康检查
// ============================================================================

/**
 * 健康检查端点
 * GET /health
 */
export const health = onRequest(
  {
    region: "asia-northeast1",
  },
  async (req, res) => {
    try {
      // 获取 Token 数量（使用聚合查询）
      const tokenCountSnapshot = await db.collection(COLLECTIONS.FCM_TOKENS)
        .count()
        .get();
      const tokenCount = tokenCountSnapshot.data().count;

      res.status(200).json({
        status: "healthy",
        timestamp: Date.now(),
        tokenCount,
        storage: "firestore",
      });
    } catch (error) {
      // 即使查询失败也返回健康状态
      res.status(200).json({
        status: "healthy",
        timestamp: Date.now(),
        storage: "firestore",
      });
    }
  },
);
