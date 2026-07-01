package com.cslearningos.mobile.ui

fun useCompactPortraitBrand(screen: AppScreen): Boolean =
    screen != AppScreen.Home

fun readerQuestionButtonLabel(openQuestionCount: Int, expanded: Boolean): String {
    val count = if (openQuestionCount > 0) " ($openQuestionCount)" else ""
    return if (expanded) "Hide Q$count" else "Q$count"
}
