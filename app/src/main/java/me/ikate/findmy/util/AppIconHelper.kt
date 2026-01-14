package me.ikate.findmy.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconHelper {
    private const val PACKAGE_NAME = "me.ikate.findmy"
    private const val ALIAS_BOY = "$PACKAGE_NAME.MainActivityBoy"
    private const val ALIAS_GIRL = "$PACKAGE_NAME.MainActivityGirl"

    enum class AppIcon {
        BOY, GIRL
    }

    fun getCurrentIcon(context: Context): AppIcon {
        val pm = context.packageManager
        val componentName = ComponentName(context, ALIAS_GIRL)
        val state = pm.getComponentEnabledSetting(componentName)
        return if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            AppIcon.GIRL
        } else {
            AppIcon.BOY
        }
    }

    fun setIcon(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        val boyComponent = ComponentName(context, ALIAS_BOY)
        val girlComponent = ComponentName(context, ALIAS_GIRL)

        if (icon == AppIcon.BOY) {
            // Enable Boy first
            pm.setComponentEnabledSetting(
                boyComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            // Disable Girl
            pm.setComponentEnabledSetting(
                girlComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } else {
            // Enable Girl first
            pm.setComponentEnabledSetting(
                girlComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            // Disable Boy
            pm.setComponentEnabledSetting(
                boyComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
