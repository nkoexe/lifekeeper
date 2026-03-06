package com.lifekeeper.app.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifekeeper.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text  = "Privacy Policy",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Effective: March 2026  ·  App in active development",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            PolicySection(
                heading = "Data collection",
                body    = "Lifekeeper does not collect, transmit, or share any personal data. " +
                    "All information you create in the app (modes, tracked time entries, and " +
                    "preferences) is stored exclusively on your device in a local database.",
            )

            PolicySection(
                heading = "Analytics & telemetry",
                body    = "No analytics, crash-reporting, or telemetry of any kind is present " +
                    "in this app. Your usage is never observed or monitored.",
            )

            PolicySection(
                heading = "Network access",
                body    = "The app requires no network permissions and makes no network " +
                    "requests. Your data never leaves your device.",
            )

            PolicySection(
                heading = "Third-party services",
                body    = "No third-party SDKs, advertising frameworks, or external services " +
                    "are used.",
            )

            PolicySection(
                heading = "Data deletion",
                body    = "You can permanently delete all your data at any time from " +
                    "Settings → Data → Reset everything, or by uninstalling the app.",
            )

            PolicySection(
                heading = "Changes to this policy",
                body    = "As Lifekeeper is in active development, this policy may be updated. " +
                    "Since no data is collected, updates will only reflect changes in app " +
                    "functionality. The current policy is always available in the app.",
            )

            PolicySection(
                heading = "Contact",
                body    = "Questions? Reach out at hi@njco.dev.",
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PolicySection(heading: String, body: String) {
    Text(
        text  = heading,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text  = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
}
