package com.skhealth.guardian.mobile

/**
 * Temporary product switches for features that are implemented but should not
 * be exposed in the current Orko Takip UI.
 *
 * Accu-Chek Instant support is intentionally kept in the codebase so it can be
 * re-enabled without rebuilding the integration when the temporary CGM period
 * ends.
 */
object FeatureFlags {
    const val ACCU_CHEK_INSTANT_UI_ENABLED: Boolean = false
}
