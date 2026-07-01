package com.cslearningos.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun MarkdownRenderer(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        markdown.lines().forEach { line ->
            when {
                line.startsWith("### ") -> Text(
                    text = line.removePrefix("### "),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                )
                line.startsWith("## ") -> Text(
                    text = line.removePrefix("## "),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
                line.startsWith("# ") -> Text(
                    text = line.removePrefix("# "),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
                )
                line.startsWith("> ") -> Text(
                    text = line.removePrefix("> "),
                    color = Color(0xFF52635A),
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .background(Color(0xFFE7F0EA))
                        .padding(10.dp)
                )
                line.trimStart().startsWith("- ") -> Text(
                    text = "- " + line.trimStart().removePrefix("- "),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                line.startsWith("```") -> Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111816))
                        .padding(8.dp),
                    color = Color(0xFFEAF5EE)
                )
                line.isBlank() -> Text(text = " ")
                else -> Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
    }
}
