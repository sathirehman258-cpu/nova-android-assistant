package com.nova.assistant.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nova", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            ListeningOrb(isActive = state.isListening || state.isThinking)

            Spacer(Modifier.height(24.dp))
            Text(state.statusText, style = MaterialTheme.typography.bodyLarge)

            if (state.transcript.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "\u201c${state.transcript}\u201d",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.lastReply.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(state.lastReply, style = MaterialTheme.typography.bodyMedium)
            }

            state.pendingConfirmationPrompt?.let { prompt ->
                Spacer(Modifier.height(16.dp))
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(prompt, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Button(onClick = viewModel::onConfirm) { Text("Yes") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = viewModel::onCancelConfirmation) { Text("Cancel") }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            FloatingActionButton(
                onClick = viewModel::onMicPressed,
                modifier = Modifier.size(72.dp)
            ) {
                Text("🎤", style = MaterialTheme.typography.headlineSmall)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ListeningOrb(isActive: Boolean) {
    val transition = rememberInfiniteTransition(label = "orb")
    val scale by transition.animateFloat(
        initialValue = if (isActive) 0.9f else 1f,
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "orbScale"
    )

    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(scale)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                ),
                shape = CircleShape
            )
    )
}
