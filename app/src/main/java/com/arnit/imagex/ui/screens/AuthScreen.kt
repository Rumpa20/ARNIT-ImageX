package com.arnit.imagex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.arnit.imagex.auth.FirebaseAuthRepository
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onBack: () -> Unit) {
    val repository = remember { FirebaseAuthRepository() }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(if (registering) "Create ImageX account" else "Sign in to ImageX",
            style = MaterialTheme.typography.headlineSmall)
        Text("Account access for premium services and secure backend requests.",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = email, onValueChange = { email = it; message = null },
            label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it; message = null },
            label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (email.isBlank() || password.length < 6) {
                    message = "Enter a valid email and password (at least 6 characters)."
                } else {
                    busy = true
                    message = null
                    scope.launch {
                        try {
                            if (registering) repository.register(email, password)
                            else repository.signIn(email, password)
                            message = "Signed in successfully."
                        } catch (e: Exception) {
                            message = e.localizedMessage ?: "Authentication failed."
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) CircularProgressIndicator()
            else Text(if (registering) "Create account" else "Sign in")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (registering) "Already have an account?" else "New to ImageX?")
            TextButton(onClick = { registering = !registering; message = null }) {
                Text(if (registering) "Sign in" else "Create account")
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(8.dp))
    }
}
