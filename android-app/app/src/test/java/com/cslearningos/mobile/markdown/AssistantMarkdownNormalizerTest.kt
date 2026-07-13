package com.cslearningos.mobile.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMarkdownNormalizerTest {
    @Test
    fun normalizeSplitsCollapsedHeadingsAndBullets() {
        val normalized = AssistantMarkdownNormalizer.normalize(
            """
            # 我的第一个 Kotlin程序##核心概念-Kotlin源文件以`.kt`结尾
            -程序入口是包级函数main
            """.trimIndent()
        )

        assertEquals(
            """
            # 我的第一个 Kotlin程序
            ## 核心概念-Kotlin源文件以`.kt`结尾
            - 程序入口是包级函数main
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun normalizeClosesBrokenFenceBeforeNextHeading() {
        val normalized = AssistantMarkdownNormalizer.normalize(
            """
            ```kotlin
            fun main() {
                println("Hello")
            }
            ## 常见错误
            - 忘记 main 参数
            """.trimIndent()
        )

        assertTrue(normalized.contains("```\n## 常见错误"))
        assertTrue(normalized.contains("- 忘记 main 参数"))
    }

    @Test
    fun normalizeSplitsMalformedFenceHeaderFromFirstCodeLine() {
        val normalized = AssistantMarkdownNormalizer.normalize(
            """
            ```kotlinpackage hello
            fun main() {}
            ```
            """.trimIndent()
        )

        assertEquals(
            """
            ```kotlin
            package hello
            fun main() {}
            ```
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun normalizeUnwrapsFenceInfoHeadingIntoPlainHeading() {
        val normalized = AssistantMarkdownNormalizer.normalize(
            """
            ```text#Output format
            - Use Markdown.
            ```
            """.trimIndent()
        )

        assertEquals(
            """
            # Output format
            - Use Markdown.
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun normalizeSplitsHeadingThatWasCollapsedIntoTableHeader() {
        val normalized = AssistantMarkdownNormalizer.normalize(
            """
            ## Examples or cases|请求类型 |示例 |是否触发 Skill |原因 |
            |----------|-----|----------------|-----|
            |简单单步任务|"读取这个 PDF"|通常不触发|Claude 直接处理更高效|
            """.trimIndent()
        )

        assertEquals(
            """
            ## Examples or cases

            |请求类型 |示例 |是否触发 Skill |原因 |
            |----------|-----|----------------|-----|
            |简单单步任务|"读取这个 PDF"|通常不触发|Claude 直接处理更高效|
            """.trimIndent(),
            normalized
        )
    }
}
