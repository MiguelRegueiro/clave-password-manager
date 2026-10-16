package com.clave.vault

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class AccountAccentTest {
    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val value = ((argb shr shift) and 255).toDouble() / 255
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
    @Test fun allPaletteTextPairsMeetNormalTextContrast() {
        for (accent in AccountAccent.entries) {
            for ((container, text) in listOf(accent.lightContainer to accent.lightText, accent.darkContainer to accent.darkText)) {
                val a = luminance(container); val b = luminance(text)
                val ratio = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
                assertTrue("${accent.name}: contrast $ratio", ratio >= 4.5)
            }
        }
    }
}
