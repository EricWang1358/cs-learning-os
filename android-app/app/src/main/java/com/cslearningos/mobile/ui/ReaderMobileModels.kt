package com.cslearningos.mobile.ui

import android.content.Context
import com.cslearningos.mobile.R

fun useCompactPortraitBrand(screen: AppScreen): Boolean =
    screenChromePolicy(screen).style == ScreenChromeStyle.Compact

fun readerQuestionButtonLabel(
    openQuestionCount: Int,
    expanded: Boolean,
    context: Context? = null
): String {
    val count = if (openQuestionCount > 0) " ($openQuestionCount)" else ""
    return if (expanded) {
        context?.getString(R.string.reader_question_button_open, count) ?: "Q open$count"
    } else {
        context?.getString(R.string.reader_question_button_closed, count) ?: "Q$count"
    }
}
