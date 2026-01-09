package me.ikate.findmy.util

import android.location.Address

object AddressFormatter {

    /**
     * 格式化地址：去除国家、省、市、区及其分割逗号和多余空格
     */
    fun formatAddress(address: Address): String {
        var fullAddress = address.getAddressLine(0) ?: return "未知位置"

        // 1. 动态移除所有已知的行政区划信息
        val components = listOfNotNull(
            address.countryName,
            address.adminArea,
            address.subAdminArea,
            address.locality,
            address.subLocality,
            "中国" // 保底移除
        )

        components.forEach { component ->
            fullAddress = fullAddress.replace(component, "")
        }

        // 2. 移除所有逗号和标点
        fullAddress = fullAddress.replace(",", "")
            .replace(",", "")
            .replace(" ", " ") // 统一空格

        // 3. 正则处理：将多个空格缩减为一个，并去除首尾空格
        fullAddress = fullAddress.replace("\\s+".toRegex(), " ").trim()

        // 4. 去除首部可能残留的连接符
        return fullAddress.trimStart('-', '、', ' ')
            .ifBlank { "未知具体位置" }
    }

}
