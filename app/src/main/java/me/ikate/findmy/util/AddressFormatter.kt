package me.ikate.findmy.util

import android.location.Address

object AddressFormatter {

    /**
     * 格式化地址：去除国家、省、市、区及其分割逗号和多余空格
     */
    fun formatAddress(address: Address): String {
        var fullAddress = address.getAddressLine(0) ?: return "未知位置"

        // 🔍 检测Plus Code格式（如"2PP7+FV5"），直接返回
        if (isPlusCode(fullAddress)) {
            return fullAddress
        }

        // 1. 动态移除所有已知的行政区划信息
        val components = listOfNotNull(
            address.countryName,
            address.adminArea,
            address.subAdminArea,
            address.locality,
            address.subLocality,
            "中国", // 保底移除
            "China" // 英文国家名也移除
        )

        components.forEach { component ->
            if (component.isNotBlank()) {
                fullAddress = fullAddress.replace(component, "")
            }
        }

        // 2. 移除所有逗号和标点
        fullAddress = fullAddress.replace(",", "")
            .replace(",", "")
            .replace("，", "")
            .replace(" ", " ") // 统一空格

        // 3. 正则处理：将多个空格缩减为一个，并去除首尾空格
        fullAddress = fullAddress.replace("\\s+".toRegex(), " ").trim()

        // 4. 去除首部可能残留的连接符
        return fullAddress.trimStart('-', '、', ' ', ',')
            .ifBlank { "未知具体位置" }
    }

    /**
     * 检测是否为Plus Code格式
     * Plus Code格式：4个字符 + "+" + 2个字符（如"2PP7+FV"）
     * 或包含Plus Code的完整格式（如"2PP7+FV5 北京市"）
     */
    fun isPlusCode(address: String): Boolean {
        // 匹配Plus Code模式：数字字母组合 + "+" + 数字字母组合
        val plusCodePattern = "[23456789CFGHJMPQRVWX]{4}\\+[23456789CFGHJMPQRVWX]{2,3}".toRegex()
        return plusCodePattern.containsMatchIn(address)
    }

}
