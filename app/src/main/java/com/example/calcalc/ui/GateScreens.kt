package com.example.calcalc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.calcalc.ServiceLocator
import com.example.calcalc.auth.SignInCancelled
import kotlinx.coroutines.launch

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { CircularProgressIndicator() }
}

/**
 * Shown when `google-services.json` is missing, which is a setup mistake rather than a
 * runtime error — so it explains the fix instead of crashing.
 */
@Composable
fun FirebaseSetupScreen(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Firebase isn't set up", style = MaterialTheme.typography.headlineSmall)
        Text(
            """
            This build has no google-services.json, so there is nowhere to store your data.

            1. Create a Firebase project and add an Android app with package
               com.example.calcalc
            2. Enable Authentication → Google
            3. Create a Firestore database
            4. Register your debug signing SHA-1
            5. Drop google-services.json into app/ and rebuild
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
fun SignInScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CalCalc", style = MaterialTheme.typography.displaySmall)
        Text(
            "Sign in with Google so your journal survives a reinstall or a new phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
        )
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                error = null
                scope.launch {
                    ServiceLocator.authRepository.signInWithGoogle(context)
                        .onFailure { if (it !is SignInCancelled) error = it.message }
                    busy = false
                }
            },
        ) { Text(if (busy) "Signing in…" else "Continue with Google") }

        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
