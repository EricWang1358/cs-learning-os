package com.cslearningos.mobile.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

sealed interface UiText {
    data class Dynamic(val value: String) : UiText

    data class Resource(
        @StringRes val resId: Int,
        val formatArgs: List<Any> = emptyList()
    ) : UiText
}

fun uiText(@StringRes resId: Int, vararg formatArgs: Any): UiText =
    UiText.Resource(resId = resId, formatArgs = formatArgs.toList())

fun UiText.resolve(context: Context): String =
    when (this) {
        is UiText.Dynamic -> value
        is UiText.Resource -> context.getString(
            resId,
            *formatArgs.map { arg ->
                when (arg) {
                    is UiText -> arg.resolve(context)
                    else -> arg
                }
            }.toTypedArray()
        )
    }

@Composable
fun UiText?.resolve(): String? =
    this?.resolve(LocalContext.current)
