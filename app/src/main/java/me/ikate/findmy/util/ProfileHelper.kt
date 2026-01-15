package me.ikate.findmy.util

import android.accounts.AccountManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log

/**
 * 机主个人信息帮助类
 * 用于获取设备所有者的联系人信息（Profile）
 * 需要 READ_CONTACTS 权限
 */
object ProfileHelper {

    private const val TAG = "ProfileHelper"

    /**
     * 机主信息数据类
     */
    data class OwnerProfile(
        val displayName: String?,
        val photoUri: Uri?,
        val source: String = "unknown"  // 来源：profile, google, samsung, device
    )

    /**
     * 导入来源枚举
     */
    enum class ProfileSource(val label: String) {
        CONTACTS_PROFILE("系统联系人"),
        GOOGLE_ACCOUNT("Google 账户"),
        SAMSUNG_ACCOUNT("三星账户"),
        DEVICE_NAME("设备名称")
    }

    /**
     * 获取所有可用的导入来源
     * @return 可用来源列表，每项包含来源类型和预览信息
     */
    fun getAvailableSources(context: Context): List<Pair<ProfileSource, OwnerProfile>> {
        val sources = mutableListOf<Pair<ProfileSource, OwnerProfile>>()

        getFromContactsProfile(context)?.let {
            sources.add(ProfileSource.CONTACTS_PROFILE to it)
        }
        getFromGoogleAccount(context)?.let {
            sources.add(ProfileSource.GOOGLE_ACCOUNT to it)
        }
        getFromSamsungAccount(context)?.let {
            sources.add(ProfileSource.SAMSUNG_ACCOUNT to it)
        }
        getFromDeviceName(context)?.let {
            sources.add(ProfileSource.DEVICE_NAME to it)
        }

        return sources
    }

    /**
     * 从指定来源获取机主信息
     */
    fun getProfileFromSource(context: Context, source: ProfileSource): OwnerProfile? {
        return when (source) {
            ProfileSource.CONTACTS_PROFILE -> getFromContactsProfile(context)
            ProfileSource.GOOGLE_ACCOUNT -> getFromGoogleAccount(context)
            ProfileSource.SAMSUNG_ACCOUNT -> getFromSamsungAccount(context)
            ProfileSource.DEVICE_NAME -> getFromDeviceName(context)
        }
    }

    /**
     * 获取机主信息（自动选择第一个可用来源）
     * @deprecated 建议使用 getAvailableSources() 让用户选择
     */
    @Deprecated("建议使用 getAvailableSources() 让用户选择来源")
    fun getOwnerProfile(context: Context): OwnerProfile? {
        return getAvailableSources(context).firstOrNull()?.second
    }

    /**
     * 从系统联系人 Profile 获取
     */
    fun getFromContactsProfile(context: Context): OwnerProfile? {
        return try {
            val projection = arrayOf(
                ContactsContract.Profile.DISPLAY_NAME,
                ContactsContract.Profile.PHOTO_URI
            )

            context.contentResolver.query(
                ContactsContract.Profile.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.Profile.DISPLAY_NAME)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.Profile.PHOTO_URI)

                    val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val photoUriStr = if (photoIndex >= 0) cursor.getString(photoIndex) else null
                    val photoUri = photoUriStr?.let { Uri.parse(it) }

                    if (!displayName.isNullOrBlank()) {
                        Log.d(TAG, "从系统 Profile 获取成功: $displayName")
                        OwnerProfile(displayName, photoUri, "profile")
                    } else null
                } else null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "没有 READ_CONTACTS 权限", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "从系统 Profile 获取失败", e)
            null
        }
    }

    /**
     * 从 Google 账户获取用户名
     */
    fun getFromGoogleAccount(context: Context): OwnerProfile? {
        return try {
            val accountManager = AccountManager.get(context)
            val accounts = accountManager.getAccountsByType("com.google")

            if (accounts.isNotEmpty()) {
                val account = accounts[0]
                // Google 账户的 name 通常是邮箱，尝试提取用户名部分
                val email = account.name
                val name = email.substringBefore("@")

                // 如果看起来像是有意义的名字（不是纯数字）
                if (name.isNotBlank() && !name.all { it.isDigit() }) {
                    Log.d(TAG, "从 Google 账户获取成功: $name")
                    OwnerProfile(name, null, "google")
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "从 Google 账户获取失败", e)
            null
        }
    }

    /**
     * 从三星账户获取用户名
     */
    fun getFromSamsungAccount(context: Context): OwnerProfile? {
        return try {
            val accountManager = AccountManager.get(context)

            // 尝试三星账户类型
            val samsungAccountTypes = listOf(
                "com.osp.app.signin",           // 三星账户
                "com.samsung.account",          // 三星账户（另一种类型）
                "com.sec.android.app.account"  // 三星账户（旧版）
            )

            for (accountType in samsungAccountTypes) {
                val accounts = accountManager.getAccountsByType(accountType)
                if (accounts.isNotEmpty()) {
                    val account = accounts[0]
                    val name = account.name

                    // 三星账户的 name 可能是邮箱或用户名
                    val displayName = if (name.contains("@")) {
                        name.substringBefore("@")
                    } else {
                        name
                    }

                    if (displayName.isNotBlank()) {
                        Log.d(TAG, "从三星账户获取成功: $displayName (type: $accountType)")
                        return OwnerProfile(displayName, null, "samsung")
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "从三星账户获取失败", e)
            null
        }
    }

    /**
     * 获取设备名称（用户在设置中自定义的设备名）
     */
    fun getFromDeviceName(context: Context): OwnerProfile? {
        return try {
            // 尝试多种方式获取设备名称
            var deviceName: String? = null

            // 方式1: Settings.Global.DEVICE_NAME (Android 7.1+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                deviceName = Settings.Global.getString(
                    context.contentResolver,
                    Settings.Global.DEVICE_NAME
                )
            }

            // 方式2: Settings.Secure (三星设备常用)
            if (deviceName.isNullOrBlank()) {
                deviceName = Settings.Secure.getString(
                    context.contentResolver,
                    "device_name"
                )
            }

            // 方式3: Settings.System
            if (deviceName.isNullOrBlank()) {
                @Suppress("DEPRECATION")
                deviceName = Settings.System.getString(
                    context.contentResolver,
                    "device_name"
                )
            }

            // 方式4: bluetooth_name (蓝牙名称通常是用户自定义的)
            if (deviceName.isNullOrBlank()) {
                deviceName = Settings.Secure.getString(
                    context.contentResolver,
                    "bluetooth_name"
                )
            }

            // 过滤掉默认设备型号名称（如 "SM-S9280"）
            if (!deviceName.isNullOrBlank() && !isDefaultDeviceName(deviceName)) {
                Log.d(TAG, "从设备名称获取成功: $deviceName")
                OwnerProfile(deviceName, null, "device")
            } else {
                Log.d(TAG, "设备名称是默认值或为空: $deviceName")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取设备名称失败", e)
            null
        }
    }

    /**
     * 判断是否是默认设备名称（型号名）
     */
    private fun isDefaultDeviceName(name: String): Boolean {
        val model = Build.MODEL
        val brand = Build.BRAND
        val device = Build.DEVICE

        return name.equals(model, ignoreCase = true) ||
                name.equals(brand, ignoreCase = true) ||
                name.equals(device, ignoreCase = true) ||
                name.startsWith("SM-", ignoreCase = true) ||  // 三星型号
                name.startsWith("Galaxy", ignoreCase = true) ||
                name.matches(Regex("^[A-Z]{2,3}-[A-Z0-9]+$"))  // 型号格式
    }

    /**
     * 从联系人 URI 获取联系人信息
     * @param context 上下文
     * @param contactUri 联系人 URI（从联系人选择器返回）
     * @return OwnerProfile 或 null
     */
    fun getContactFromUri(context: Context, contactUri: Uri): OwnerProfile? {
        return try {
            val projection = arrayOf(
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_URI
            )

            context.contentResolver.query(
                contactUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

                    val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val photoUriStr = if (photoIndex >= 0) cursor.getString(photoIndex) else null
                    val photoUri = photoUriStr?.let { Uri.parse(it) }

                    Log.d(TAG, "从联系人获取信息成功: name=$displayName, photo=$photoUri")
                    OwnerProfile(displayName, photoUri)
                } else {
                    Log.d(TAG, "联系人信息为空")
                    null
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "没有 READ_CONTACTS 权限", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "获取联系人信息失败", e)
            null
        }
    }

    /**
     * 检查是否有 READ_CONTACTS 权限
     */
    fun hasReadContactsPermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
