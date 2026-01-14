package me.ikate.findmy.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import me.ikate.findmy.service.LostModeService

/**
 * 丢失模式身份验证 Activity
 * 透明 Activity，用于显示生物识别/设备凭证验证对话框
 * 验证成功后关闭丢失模式
 */
class LostModeAuthActivity : FragmentActivity() {

    companion object {
        private const val TAG = "LostModeAuthActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查生物识别可用性
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                showBiometricPrompt()
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                // 设备不支持生物识别，尝试使用设备凭证
                showDeviceCredentialPrompt()
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // 未注册生物识别，尝试使用设备凭证
                showDeviceCredentialPrompt()
            }
            else -> {
                Log.w(TAG, "无法使用身份验证: $canAuthenticate")
                Toast.makeText(this, "无法验证身份", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)

        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "身份验证成功")
                    onAuthSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.w(TAG, "身份验证错误: $errorCode - $errString")
                    onAuthFailed(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "身份验证失败")
                    // 不关闭 Activity，让用户重试
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("验证机主身份")
            .setSubtitle("请验证您是此设备的机主")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showDeviceCredentialPrompt() {
        val executor = ContextCompat.getMainExecutor(this)

        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "设备凭证验证成功")
                    onAuthSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.w(TAG, "设备凭证验证错误: $errorCode - $errString")
                    onAuthFailed(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "设备凭证验证失败")
                }
            }
        )

        try {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("验证机主身份")
                .setSubtitle("请输入设备 PIN/密码/图案")
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "无法显示设备凭证对话框", e)
            Toast.makeText(this, "无法验证身份: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun onAuthSuccess() {
        // 验证成功，关闭丢失模式
        LostModeService.disable(this)
        Toast.makeText(this, "丢失模式已关闭", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun onAuthFailed(message: String) {
        // 验证失败或取消
        if (message.contains("取消", ignoreCase = true) ||
            message.contains("cancel", ignoreCase = true)
        ) {
            Toast.makeText(this, "已取消验证", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "验证失败: $message", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
