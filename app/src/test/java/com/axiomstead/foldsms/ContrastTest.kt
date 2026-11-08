package com.axiomstead.foldsms

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private fun check(name: String, scheme: ColorScheme) {
        val pairs: List<Triple<String, Color, Color>> = listOf(
            Triple("text on surface", scheme.onSurface, scheme.surface), Triple("text on background", scheme.onSurface, scheme.background),
            Triple("secondary text on surface", scheme.onSurfaceVariant, scheme.surface), Triple("secondary text on background", scheme.onSurfaceVariant, scheme.background),
            Triple("secondary text on search field", scheme.onSurfaceVariant, scheme.surfaceVariant),
            Triple("accent text on surface", scheme.primary, scheme.surface), Triple("accent text on background", scheme.primary, scheme.background),
            Triple("accent text on selected row", scheme.primary, scheme.surfaceVariant), Triple("button text", scheme.onPrimary, scheme.primary)
        )
        pairs.forEach { (what, fg, bg) ->
            val ratio = contrastRatio(fg, bg)
            assertTrue("$name $what is only ${"%.2f".format(ratio)}:1, below 4.5:1", ratio >= 4.5)
        }
        // Avatar initials are large bold text: 3:1 is enough there.
        listOf(scheme.primary, scheme.secondary, scheme.onSurfaceVariant).forEach { tone ->
            assertTrue("$name avatar tone", contrastRatio(scheme.surface, tone) >= 3.0)
        }
    }

    @Test fun theLightColoursMeetAA() = check("light", lightAppColors)
    @Test fun theDarkColoursMeetAA() = check("dark", darkAppColors)

    @Test fun theRatioFunctionMatchesKnownValues() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrastRatio(Color.White, Color.White), 0.001)
        assertEquals(4.0, contrastRatio(Color(0xFF777777), Color.White), 0.6) // grey 777 on white is about 4.48
    }
}
