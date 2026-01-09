package me.ikate.findmy.util

import android.content.Context
import android.content.Intent

/**
 * 分享工具类
 * 提供应用内各种内容的分享功能
 */
object ShareHelper {

    /**
     * 分享文本内容
     * @param context 上下文
     * @param text 要分享的文本内容
     * @param title 分享选择器的标题，默认为"分享"
     */
    fun shareText(context: Context, text: String, title: String = "分享") {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(shareIntent, title))
    }

    /**
     * 分享 UID
     * @param context 上下文
     * @param uid 用户的 UID
     */
    fun shareUid(context: Context, uid: String) {
        val shareText = "我的 Find My UID: $uid\n\n添加我来共享位置！"
        shareText(context, shareText, "分享我的 UID")
    }

    /**
     * 分享位置
     * @param context 上下文
     * @param latitude 纬度
     * @param longitude 经度
     * @param locationName 位置名称（可选）
     */
    fun shareLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        locationName: String? = null
    ) {
        val locationText = if (locationName != null) {
            "$locationName\n位置: $latitude, $longitude\nhttps://maps.google.com/?q=$latitude,$longitude"
        } else {
            "位置: $latitude, $longitude\nhttps://maps.google.com/?q=$latitude,$longitude"
        }
        shareText(context, locationText, "分享位置")
    }
}
