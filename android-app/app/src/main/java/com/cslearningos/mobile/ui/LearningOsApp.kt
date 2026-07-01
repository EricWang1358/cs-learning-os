package com.cslearningos.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.SearchResultEntity
import com.cslearningos.mobile.domain.ReviewRating
import java.text.DateFormat
import java.util.Date

@Composable
fun LearningOsApp(viewModel: LearningViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Header(state = state, viewModel = viewModel)
                if (state.message.isNotBlank()) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                when (state.screen) {
                    AppScreen.Home -> HomeScreen(state, viewModel)
                    AppScreen.Reader -> ReaderScreen(state, viewModel)
                    AppScreen.Editor -> EditorScreen(state, viewModel)
                    AppScreen.Search -> SearchScreen(state, viewModel)
                    AppScreen.QuizEditor -> QuizEditorScreen(state, viewModel)
                    AppScreen.Review -> ReviewScreen(state, viewModel)
                    AppScreen.Backup -> BackupScreen(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun Header(state: LearningUiState, viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "CS Learning OS",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = viewModel::showHome) { Text("Home") }
            TextButton(onClick = viewModel::showSearch) { Text("Search") }
            TextButton(onClick = viewModel::showReview) { Text("Review (${state.dueQuizzes.size})") }
            TextButton(onClick = viewModel::showBackup) { Text("Backup") }
        }
    }
}

@Composable
private fun HomeScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = viewModel::startNewNode) {
            Text("New Markdown node")
        }
        if (state.nodes.isEmpty()) {
            Text("No local nodes yet. Create one and this APK becomes independent.")
        }
        state.nodes.forEach { node ->
            NodeCard(node = node, viewModel = viewModel)
        }
    }
}

@Composable
private fun NodeCard(node: LearningNodeEntity, viewModel: LearningViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(node.title, style = MaterialTheme.typography.titleMedium)
            Text("Updated ${formatTime(node.updatedAt)}")
            Text("Last read ${node.lastReadAt?.let(::formatTime) ?: "not recorded"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.openNode(node) }) { Text("Read") }
                TextButton(onClick = { viewModel.editNode(node) }) { Text("Edit") }
            }
        }
    }
}

@Composable
private fun ReaderScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val node = state.selectedNode
    if (node == null) {
        Text("No node selected.")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(node.title, style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.editNode(node) }) { Text("Edit") }
            Button(onClick = viewModel::startQuizForSelectedNode) { Text("Add quiz") }
        }
        MarkdownRenderer(markdown = node.markdownBody)
    }
}

@Composable
private fun EditorScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = state.editorTitle,
            onValueChange = viewModel::setEditorTitle,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.editorBody,
            onValueChange = viewModel::setEditorBody,
            label = { Text("Markdown") },
            minLines = 14,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = viewModel::saveNode) {
            Text("Save node")
        }
    }
}

@Composable
private fun SearchScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            label = { Text("Search local nodes and quizzes") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = viewModel::runSearch) { Text("Search") }
        state.searchResults.forEach { result ->
            SearchResultCard(result)
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResultEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("${result.type}: ${result.title}", fontWeight = FontWeight.Bold)
            Text(result.snippet)
        }
    }
}

@Composable
private fun QuizEditorScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Quiz for ${state.selectedNode?.title ?: "general review"}")
        OutlinedTextField(
            value = state.quizPrompt,
            onValueChange = viewModel::setQuizPrompt,
            label = { Text("Question") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.quizAnswer,
            onValueChange = viewModel::setQuizAnswer,
            label = { Text("Answer") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.quizExplanation,
            onValueChange = viewModel::setQuizExplanation,
            label = { Text("Explanation") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = viewModel::saveQuiz) { Text("Save quiz") }
    }
}

@Composable
private fun ReviewScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val quiz = state.selectedQuiz
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (quiz == null) {
            Text("No due cards right now.")
            Text("Total quizzes: ${state.quizzes.size}")
            return
        }
        QuizCard(quiz = quiz)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.answerCurrentQuiz(ReviewRating.Again) }) { Text("Again") }
            Button(onClick = { viewModel.answerCurrentQuiz(ReviewRating.Hard) }) { Text("Hard") }
            Button(onClick = { viewModel.answerCurrentQuiz(ReviewRating.Good) }) { Text("Good") }
        }
    }
}

@Composable
private fun QuizCard(quiz: QuizItemEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(quiz.prompt, style = MaterialTheme.typography.titleMedium)
            Text("Answer: ${quiz.answer}")
            if (quiz.explanation.isNotBlank()) {
                Text("Why: ${quiz.explanation}")
            }
        }
    }
}

@Composable
private fun BackupScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::exportBackup) { Text("Export JSON") }
            Button(onClick = viewModel::restoreBackup) { Text("Restore overwrite") }
        }
        OutlinedTextField(
            value = state.backupText,
            onValueChange = viewModel::setBackupText,
            label = { Text("Backup JSON") },
            minLines = 12,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun formatTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))
