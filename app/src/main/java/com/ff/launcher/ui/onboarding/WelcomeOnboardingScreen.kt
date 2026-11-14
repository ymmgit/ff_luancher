package com.ff.launcher.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ff.launcher.R

/**
 * Modern Google Material 3 first-run welcome screen.
 */
@Composable
fun WelcomeOnboardingScreen(
    isDefaultLauncher: Boolean,
    onSetDefaultClick: () -> Unit,
    onCompleteOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    var step by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = insets.calculateTopPadding() + 40.dp,
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.3f))
        OnboardingCard(
            title = when (step) {
                0 -> stringResource(R.string.onboarding_title)
                1 -> stringResource(R.string.onboarding_feature_minimal)
                else -> stringResource(R.string.onboarding_feature_scroller)
            },
            description = when (step) {
                0 -> stringResource(R.string.onboarding_subtitle)
                1 -> stringResource(R.string.onboarding_feature_minimal_desc)
                else -> stringResource(R.string.onboarding_feature_scroller_desc)
            },
            buttonText = when (step) {
                0 -> if (isDefaultLauncher) {
                    stringResource(R.string.onboarding_next)
                } else {
                    stringResource(R.string.onboarding_set_default)
                }
                1 -> stringResource(R.string.onboarding_next)
                else -> stringResource(R.string.onboarding_get_started)
            },
            onButtonClick = {
                if (step == 0 && !isDefaultLauncher) onSetDefaultClick()
                if (step < 2) step += 1 else onCompleteOnboarding()
            },
        )
        Spacer(Modifier.height(32.dp))
        TextButton(onClick = { onCompleteOnboarding() }) {
            Text(
                text = stringResource(R.string.onboarding_skip),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OnboardingCard(
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onButtonClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
