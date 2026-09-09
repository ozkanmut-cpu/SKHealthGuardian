package com.skhealth.guardian.shared

object SpO2ReliabilityClassifier {
    fun label(count: Int, meanAbsoluteError: Double?, meanBias: Double?): String = when {
        count < 5 -> "Yetersiz eşleşme"
        meanAbsoluteError != null && meanAbsoluteError <= 2.0 -> "Yüksek uyum"
        meanAbsoluteError != null && meanAbsoluteError <= 4.0 -> "Orta uyum"
        meanBias != null && meanBias >= 5.0 -> "Saat sistematik yüksek okuyor"
        meanBias != null && meanBias <= -5.0 -> "Saat sistematik düşük okuyor"
        else -> "Düşük uyum"
    }
}
