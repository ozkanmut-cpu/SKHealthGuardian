package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class SpO2ReliabilityClassifierTest {
    @Test fun requiresAtLeastFiveMatches() =
        assertEquals("Yetersiz eşleşme", SpO2ReliabilityClassifier.label(4, 1.0, 0.5))

    @Test fun highAgreementAtTwoPointsOrLess() =
        assertEquals("Yüksek uyum", SpO2ReliabilityClassifier.label(5, 2.0, 1.0))

    @Test fun mediumAgreementUpToFourPoints() =
        assertEquals("Orta uyum", SpO2ReliabilityClassifier.label(8, 3.5, 2.0))

    @Test fun systematicHighBiasIsFlagged() =
        assertEquals("Saat sistematik yüksek okuyor", SpO2ReliabilityClassifier.label(10, 6.0, 5.5))

    @Test fun systematicLowBiasIsFlagged() =
        assertEquals("Saat sistematik düşük okuyor", SpO2ReliabilityClassifier.label(10, 6.0, -5.5))

    @Test fun otherwiseLowAgreement() =
        assertEquals("Düşük uyum", SpO2ReliabilityClassifier.label(10, 5.0, 1.0))
}
