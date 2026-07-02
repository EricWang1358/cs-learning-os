package com.cslearningos.mobile.feature.capture.ui

import com.cslearningos.mobile.data.CaptureSlipType

data class CaptureUiState(
    val draft: String = "",
    val topicHint: String = "",
    val sourceLabel: String = "",
    val type: CaptureSlipType = CaptureSlipType.unclear,
    val messageKey: String? = null
)
