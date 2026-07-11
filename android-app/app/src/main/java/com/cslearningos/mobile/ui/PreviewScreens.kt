package com.cslearningos.mobile.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Workbench Card Accent")
@Composable
fun WorkbenchCardPreview() {
    WorkbenchTheme {
        WorkbenchCard(accent = true) {
            Text("Card Preview", color = WorkbenchColors.InkStrong)
        }
    }
}

@Preview(showBackground = true, name = "Workbench Card Normal")
@Composable
fun WorkbenchCardNormalPreview() {
    WorkbenchTheme {
        WorkbenchCard(accent = false) {
            Text("Normal Card", color = WorkbenchColors.InkStrong)
        }
    }
}

@Preview(showBackground = true, name = "Workbench Button Primary")
@Composable
fun WorkbenchButtonPreview() {
    WorkbenchTheme {
        WorkbenchButton("Primary Button", onClick = {}, primary = true)
    }
}

@Preview(showBackground = true, name = "Workbench Button Secondary")
@Composable
fun WorkbenchButtonSecondaryPreview() {
    WorkbenchTheme {
        WorkbenchButton("Secondary Button", onClick = {}, primary = false)
    }
}

@Preview(showBackground = true, name = "Empty Workbench Card")
@Composable
fun EmptyWorkbenchCardPreview() {
    WorkbenchTheme {
        EmptyWorkbenchCard(
            title = "No Recent Learning",
            body = "Start capturing ideas to begin your learning journey."
        )
    }
}