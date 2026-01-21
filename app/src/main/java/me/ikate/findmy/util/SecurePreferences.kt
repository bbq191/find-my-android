@file:Suppress("DEPRECATION")

package me.ikate.findmy.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore
import javax.crypto.AEADBadTagException

/**
 * 加密存储工具类
 * 使用 EncryptedSharedPreferences 安全存储敏感数据
 *
 * 注：EncryptedSharedPreferences 在 Java 中标记为 deprecated，
 * 但目前没有官方替代方案，仍是推荐的加密存储方式
 */
object SecurePreferences {

    private const val TAG = "SecurePreferences"
    private const val PREFS_FILE_NAME = "secure_user_profile"
    private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"

    @Volatile
    private var instance: SharedPreferences? = null

    /**
     * 在应用启动时初始化，预先检测并修复加密存储问题
     * 应该在 Application.onCreate() 中调用，在 Koin 初始化之前
     */
    fun init(context: Context) {
        synchronized(this) {
            if (instance != null) return

            // 检查是否需要重置（文件不完整的情况）
            if (needsResetDueToIncompleteFiles(context)) {
                Log.w(TAG, "检测到加密存储文件不完整，执行重置...")
                resetEncryptedStorage(context)
            }

            // 尝试创建实例
            instance = createEncryptedPreferences(context)
        }
    }

    /**
     * 获取加密的 SharedPreferences 实例
     * 如果加密失败，会回退到普通 SharedPreferences（确保应用可用）
     */
    fun getInstance(context: Context): SharedPreferences {
        return instance ?: synchronized(this) {
            instance ?: createEncryptedPreferences(context).also { instance = it }
        }
    }

    /**
     * 检测是否因为文件不完整需要重置
     */
    private fun needsResetDueToIncompleteFiles(context: Context): Boolean {
        val sharedPrefsDir = File(context.filesDir.parent, "shared_prefs")
        val prefsFile = File(sharedPrefsDir, "$PREFS_FILE_NAME.xml")
        val keysetFile = File(sharedPrefsDir, "__androidx_security_crypto_encrypted_prefs__$PREFS_FILE_NAME.xml")

        // 如果两个文件都不存在，说明是全新安装，不需要重置
        if (!prefsFile.exists() && !keysetFile.exists()) {
            return false
        }

        // 如果只存在其中一个文件，说明数据可能损坏
        if (prefsFile.exists() != keysetFile.exists()) {
            Log.w(TAG, "加密存储文件不完整: prefs=${prefsFile.exists()}, keyset=${keysetFile.exists()}")
            return true
        }

        return false
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        return try {
            createEncryptedPreferencesInternal(context)
        } catch (e: Exception) {
            // 检查是否是密钥不匹配的错误
            if (isKeyMismatchError(e)) {
                Log.w(TAG, "检测到密钥不匹配，重置加密存储并回退到普通存储")
                // 重置存储，下次启动时会自动创建新的加密存储
                resetEncryptedStorage(context)
                // 本次回退到普通存储（因为 SharedPreferences 缓存问题，重置后立即重试可能不会生效）
                Log.d(TAG, "已回退到普通存储，下次启动将使用新的加密存储")
                context.getSharedPreferences(PREFS_FILE_NAME + "_fallback", Context.MODE_PRIVATE)
            } else {
                Log.e(TAG, "创建加密存储失败，回退到普通存储", e)
                context.getSharedPreferences(PREFS_FILE_NAME + "_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    /**
     * 创建加密存储的内部实现
     */
    private fun createEncryptedPreferencesInternal(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 检查是否是密钥不匹配的错误
     */
    private fun isKeyMismatchError(e: Exception): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is AEADBadTagException ||
                cause.message?.contains("VERIFICATION_FAILED") == true ||
                cause.message?.contains("Signature/MAC verification failed") == true) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    /**
     * 重置加密存储（删除损坏的数据和密钥）
     */
    private fun resetEncryptedStorage(context: Context) {
        val sharedPrefsDir = File(context.filesDir.parent, "shared_prefs")

        // 1. 先通过 SharedPreferences API 清除数据（确保内存缓存也被清除）
        try {
            val prefsToClean = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
            prefsToClean.edit().clear().commit() // 使用 commit 确保同步完成
        } catch (e: Exception) {
            Log.w(TAG, "清除 SharedPreferences 缓存失败", e)
        }

        // 清除 keyset SharedPreferences 的缓存
        val keysetPrefsName = "__androidx_security_crypto_encrypted_prefs__$PREFS_FILE_NAME"
        try {
            val keysetPrefs = context.getSharedPreferences(keysetPrefsName, Context.MODE_PRIVATE)
            keysetPrefs.edit().clear().commit()
        } catch (e: Exception) {
            Log.w(TAG, "清除 keyset SharedPreferences 缓存失败", e)
        }

        // 2. 删除加密的 SharedPreferences 文件
        val prefsFile = File(sharedPrefsDir, "$PREFS_FILE_NAME.xml")
        if (prefsFile.exists()) {
            val deleted = prefsFile.delete()
            Log.d(TAG, "删除加密存储文件: $deleted")
        }

        // 3. 删除 Tink keyset 文件（EncryptedSharedPreferences 使用这个存储加密密钥）
        val keysetFile = File(sharedPrefsDir, "$keysetPrefsName.xml")
        if (keysetFile.exists()) {
            val deleted = keysetFile.delete()
            Log.d(TAG, "删除 Tink keyset 文件: $deleted")
        }

        // 4. 删除可能的备份文件
        val backupFile = File(sharedPrefsDir, "$PREFS_FILE_NAME.xml.bak")
        if (backupFile.exists()) {
            backupFile.delete()
        }
        val keysetBackupFile = File(sharedPrefsDir, "$keysetPrefsName.xml.bak")
        if (keysetBackupFile.exists()) {
            keysetBackupFile.delete()
        }

        // 5. 删除 Android Keystore 中的主密钥
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
                Log.d(TAG, "已删除 Keystore 中的主密钥")
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除 Keystore 密钥失败", e)
        }

        // 6. 清除内存中的缓存
        instance = null

        Log.d(TAG, "加密存储重置完成")
    }

    /**
     * 迁移旧的非加密数据到加密存储
     * 应该在应用启动时调用一次
     */
    fun migrateFromPlainPrefs(context: Context, oldPrefsName: String) {
        try {
            val oldPrefs = context.getSharedPreferences(oldPrefsName, Context.MODE_PRIVATE)
            val newPrefs = getInstance(context)

            // 检查是否已经迁移过
            if (oldPrefs.all.isEmpty()) {
                return
            }

            // 迁移所有数据
            val editor = newPrefs.edit()
            for ((key, value) in oldPrefs.all) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(key, value as Set<String>)
                    }
                }
            }
            editor.apply()

            // 清除旧数据
            oldPrefs.edit().clear().apply()
            Log.d(TAG, "数据迁移完成: $oldPrefsName -> $PREFS_FILE_NAME")
        } catch (e: Exception) {
            Log.e(TAG, "数据迁移失败", e)
        }
    }
}
