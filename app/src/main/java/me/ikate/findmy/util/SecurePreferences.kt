@file:Suppress("DEPRECATION")

package me.ikate.findmy.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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

    @Volatile
    private var instance: SharedPreferences? = null

    /**
     * 获取加密的 SharedPreferences 实例
     * 如果加密失败，会回退到普通 SharedPreferences（确保应用可用）
     */
    fun getInstance(context: Context): SharedPreferences {
        return instance ?: synchronized(this) {
            instance ?: createEncryptedPreferences(context).also { instance = it }
        }
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "创建加密存储失败，回退到普通存储", e)
            // 回退到普通 SharedPreferences
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
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
