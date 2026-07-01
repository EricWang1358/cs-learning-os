package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

data class MoreSectionSummary(
    val id: MoreSectionId,
    val title: String,
    val body: String,
    val value: String
)

@Composable
fun moreSectionSummaries(state: LearningUiState): List<MoreSectionSummary> =
    orderedMoreSectionIds().map { sectionId ->
        when (sectionId) {
            MoreSectionId.System -> MoreSectionSummary(
                id = sectionId,
                title = stringResource(R.string.more_section_system_title),
                body = stringResource(R.string.more_section_system_body),
                value = "${stringResource(systemLanguageLabelResId(state.systemLanguage))} / ${stringResource(appearanceModeLabelResId(state.appearanceMode))}"
            )

            MoreSectionId.Service -> MoreSectionSummary(
                id = sectionId,
                title = stringResource(R.string.more_section_service_title),
                body = stringResource(R.string.more_section_service_body),
                value = stringResource(if (state.aiProviderSettings.isConfigured) R.string.common_configured else R.string.common_not_configured)
            )

            MoreSectionId.Notifications -> MoreSectionSummary(
                id = sectionId,
                title = stringResource(R.string.more_section_notifications_title),
                body = stringResource(R.string.more_section_notifications_body),
                value = stringResource(R.string.more_value_inbox)
            )

            MoreSectionId.Data -> MoreSectionSummary(
                id = sectionId,
                title = stringResource(R.string.more_section_data_title),
                body = stringResource(R.string.more_section_data_body),
                value = stringResource(R.string.more_value_local_first)
            )

            MoreSectionId.Guide -> MoreSectionSummary(
                id = sectionId,
                title = stringResource(R.string.more_section_guide_title),
                body = stringResource(R.string.more_section_guide_body),
                value = stringResource(R.string.more_value_start_here)
            )

            MoreSectionId.Support -> MoreSectionSummary(
                id = sectionId,
                title = stringResource(R.string.more_section_support_title),
                body = stringResource(R.string.more_section_support_body),
                value = stringResource(R.string.more_value_offline_safe)
            )
        }
    }

@Composable
fun systemLanguageLabel(language: SystemLanguage): String =
    stringResource(systemLanguageLabelResId(language))

@Composable
fun appearanceModeLabel(mode: AppearanceMode): String =
    stringResource(appearanceModeLabelResId(mode))
