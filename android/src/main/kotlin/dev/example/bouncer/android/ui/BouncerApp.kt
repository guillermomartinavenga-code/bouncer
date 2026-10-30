package dev.example.bouncer.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.example.bouncer.android.security.BiometricUnlocker
import dev.example.bouncer.android.security.KeystoreTokenStorage
import dev.example.bouncer.shared.domain.model.Role
import dev.example.bouncer.shared.presentation.SessionViewModel
import dev.example.bouncer.shared.presentation.UiState

@Composable
fun BouncerApp(storage: KeystoreTokenStorage, unlocker: BiometricUnlocker) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            val sessionViewModel = rememberViewModel<SessionViewModel>()
            val state by sessionViewModel.uiState.collectAsState()

            // A saved session exists on disk but its refresh token is still encrypted: nothing
            // can talk to the backend until the biometric prompt succeeds.
            var locked by remember { mutableStateOf(storage.isLocked()) }
            var unlockMessage by remember { mutableStateOf<String?>(null) }

            if (locked) {
                UnlockScreen(
                    message = unlockMessage,
                    onUnlock = {
                        unlocker.unlock(
                            onUnlocked = {
                                locked = false
                                unlockMessage = null
                                sessionViewModel.restore()
                            },
                            onFailed = { unlockMessage = it },
                        )
                    },
                    onUseAnotherAccount = {
                        storage.discardSavedSession()
                        locked = false
                        unlockMessage = null
                    },
                )
                return@Surface
            }

            when (val current = state) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Content -> {
                    val user = current.data
                    if (user == null) {
                        LoginScreen(error = null, onLogin = sessionViewModel::login)
                    } else {
                        when (user.role) {
                            Role.CUSTOMER -> CustomerHome(user, sessionViewModel::logout, sessionViewModel::onSessionExpired)
                            Role.DISPATCHER -> DispatcherHome(user, sessionViewModel::logout, sessionViewModel::onSessionExpired)
                            Role.ADMIN -> WrongClientScreen(onLogout = sessionViewModel::logout)
                        }
                    }
                }
                is UiState.Error -> LoginScreen(error = current, onLogin = sessionViewModel::login)
            }
        }
    }
}

@Composable
private fun LoginScreen(error: UiState.Error?, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Bouncer", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) ErrorMessage(error)
        Button(onClick = { onLogin(email.trim(), password) }, modifier = Modifier.fillMaxWidth()) {
            Text("Log in")
        }
        Text(
            "Demo users (password: password123): ana@example.com / beto@example.com (customers), " +
                "dani@example.com (dispatcher).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun UnlockScreen(message: String?, onUnlock: () -> Unit, onUseAnotherAccount: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Session locked", style = MaterialTheme.typography.headlineMedium)
        Text("A saved session was found. Unlock it with your fingerprint to continue.")
        if (message != null) Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) { Text("Unlock with biometrics") }
        OutlinedButton(onClick = onUseAnotherAccount, modifier = Modifier.fillMaxWidth()) {
            Text("Forget session and log in again")
        }
    }
}

@Composable
private fun WrongClientScreen(onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Text("Admins use the web app.", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onLogout) { Text("Log out") }
    }
}
