package com.callbundle.callbundle_android

import android.os.Build
import android.util.Log

/**
 * Detects budget OEM devices and recommends notification strategies.
 *
 * Many budget Indian OEM devices (LAVA, Micromax, itel, Tecno, Infinix,
 * Realme) have non-standard notification behavior that can cause
 * incoming call notifications to silently fail. This class detects
 * the device manufacturer and recommends the optimal strategy.
 *
 * ## Strategy Tiers
 *
 * 1. **Standard:** Uses `NotificationCompat.CallStyle` (API 31+)
 *    or standard notification with action buttons.
 * 2. **OEM-Adaptive:** Uses simpler notification layouts that are
 *    known to work on budget SOCs. Avoids `RemoteViews` entirely.
 * 3. **Aggressive:** Additionally sets higher priority, uses
 *    heads-up display, and configures full-screen intent as
 *    the primary display mechanism.
 */
class OemDetector {

    companion object {
        private const val TAG = "OemDetector"

        /**
         * Budget OEMs known to have notification reliability issues.
         *
         * This list is maintained based on real-world device testing
         * and user reports from the Indian market.
         */
        private val BUDGET_OEMS = setOf(
            "lava",
            "micromax",
            "itel",
            "tecno",
            "infinix",
            "realme",
            "karbonn",
            "intex",
            "gionee",
            "lyf",
            "10.or",
            "comio",
            "ivoomi",
            "tambo",
            "xolo",
            "celkon",
            "coolpad",
            "leeco",
            "lenovomt"  // Lenovo MediaTek variants
        )

        /**
         * OEMs with aggressive battery optimization that may kill
         * background services and suppress notifications.
         */
        private val AGGRESSIVE_BATTERY_OEMS = setOf(
            "xiaomi",
            "huawei",
            "honor",
            "oppo",
            "vivo",
            "oneplus",
            "meizu",
            "samsung"  // Samsung OneUI has gotten more aggressive
        )
    }

    /**
     * Returns the lowercase device manufacturer name.
     */
    fun getManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }

    /**
     * Returns the device model.
     */
    fun getModel(): String {
        return Build.MODEL
    }

    /**
     * Returns the Android SDK version.
     */
    fun getSdkVersion(): Int {
        return Build.VERSION.SDK_INT
    }

    /**
     * Whether this device is from a known budget OEM with
     * notification reliability issues.
     */
    fun isBudgetOem(): Boolean {
        val manufacturer = getManufacturer()
        return BUDGET_OEMS.contains(manufacturer)
    }

    /**
     * Whether this device has aggressive battery optimization
     * that may affect background notification delivery.
     */
    fun hasAggressiveBatteryOptimization(): Boolean {
        val manufacturer = getManufacturer()
        return AGGRESSIVE_BATTERY_OEMS.contains(manufacturer)
    }

    /**
     * Returns the recommended notification strategy for this device.
     *
     * @return One of "standard", "adaptive", or "aggressive".
     */
    fun getRecommendedStrategy(): String {
        return when {
            isBudgetOem() -> "aggressive"
            hasAggressiveBatteryOptimization() -> "adaptive"
            else -> "standard"
        }
    }

    /**
     * Returns comprehensive diagnostic information about the device.
     */
    fun getDiagnostics(): Map<String, Any> {
        return mapOf(
            "manufacturer" to getManufacturer(),
            "model" to getModel(),
            "sdkVersion" to getSdkVersion(),
            "isBudgetOem" to isBudgetOem(),
            "hasAggressiveBattery" to hasAggressiveBatteryOptimization(),
            "recommendedStrategy" to getRecommendedStrategy(),
            "brand" to Build.BRAND.lowercase(),
            "device" to Build.DEVICE,
            "product" to Build.PRODUCT
        )
    }
}
