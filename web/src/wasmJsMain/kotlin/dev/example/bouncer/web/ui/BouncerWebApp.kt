package dev.example.bouncer.web.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import dev.example.bouncer.shared.domain.model.Role
import dev.example.bouncer.shared.presentation.SessionViewModel
import dev.example.bouncer.shared.presentation.UiState

@Composable
fun BouncerWebApp() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 960.dp).fillMaxSize()) { AppContent() }
            }
        }
    }
}

@Composable
private fun AppContent() {
    val sessionViewModel = rememberViewModel<SessionViewModel>()
    val state by sessionViewModel.uiState.collectAsState()
    // Hoisted so a failed login (which swaps the whole screen out) doesn't wipe what was typed.
    var email by remember { mutableStateOf("") }

    when (val current = state) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Content -> {
            val user = current.data
            if (user == null) {
                LoginScreen(email, { email = it }, error = null, onLogin = sessionViewModel::login)
            } else {
                when (user.role) {
                    Role.CUSTOMER -> CustomerHome(user, sessionViewModel::logout, sessionViewModel::onSessionExpired)
                    Role.ADMIN -> AdminHome(user, sessionViewModel::logout, sessionViewModel::onSessionExpired)
                    Role.DISPATCHER -> WrongClientScreen(onLogout = sessionViewModel::logout)
                }
            }
        }
        is UiState.Error -> LoginScreen(email, { email = it }, error = current, onLogin = sessionViewModel::login)
    }
}

@Composable
private fun LoginScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    error: UiState.Error?,
    onLogin: (String, String) -> Unit,
) {
    var password by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Bouncer", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
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
                "caro@example.com (admin).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun WrongClientScreen(onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Dispatchers use the Android app.", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onLogout) { Text("Log out") }
    }
}
