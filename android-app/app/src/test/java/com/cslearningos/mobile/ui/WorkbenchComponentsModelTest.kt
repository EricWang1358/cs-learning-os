package com.cslearningos.mobile.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbenchComponentsModelTest {
    @Test
    fun shortChineseEyebrowsUseTighterLetterSpacing() {
        val compact = eyebrowLetterSpacingValue("快速捕捉")
        val latin = eyebrowLetterSpacingValue("notification")

        assertTrue(compact < latin)
        assertTrue(compact <= 0.2f)
    }
}
