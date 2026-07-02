package com.cslearningos.mobile.feature.capture.ui

import androidx.lifecycle.ViewModel
import com.cslearningos.mobile.feature.capture.data.CaptureRepository
import com.cslearningos.mobile.feature.capture.domain.GenerateCaptureDraftUseCase

class CaptureViewModel(
    private val repository: CaptureRepository,
    private val generateCaptureDraft: GenerateCaptureDraftUseCase
) : ViewModel()
