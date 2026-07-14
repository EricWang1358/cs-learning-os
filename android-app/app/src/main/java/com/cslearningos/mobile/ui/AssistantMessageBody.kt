package com.cslearningos.mobile.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun AssistantMessageBody(markdown: String) {
    SelectionContainer(modifier = Modifier.testTag("assistant-message-body")) {
        MarkdownRenderer(markdown = markdown, card = false)
    }
}
