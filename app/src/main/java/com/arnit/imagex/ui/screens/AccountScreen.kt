package com.arnit.imagex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AccountScreen(onBack: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var user by remember { mutableStateOf(auth.currentUser) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Account", style = MaterialTheme.typography.headlineMedium)
        user?.let {
            Text("Signed in as: ${it.email ?: "Firebase user"}")
            Button(onClick = { auth.signOut(); user = null; message = "Signed out" }) {
                Text("Sign out")
            }
        } ?: run {
            Text(if (createAccount) "Create your ImageX account" else "Sign in to ImageX")
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    message = ""
                    val task = if (createAccount) auth.createUserWithEmailAndPassword(email.trim(), password)
                        else auth.signInWithEmailAndPassword(email.trim(), password)
                    task.addOnCompleteListener { result ->
                        if (result.isSuccessful) {
                            user = auth.currentUser
                            message = if (createAccount) "Account created" else "Signed in"
                        } else message = result.exception?.localizedMessage ?: "Authentication failed"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (createAccount) "Create account" else "Sign in") }
            Button(onClick = { createAccount = !createAccount; message = "" }, modifier = Modifier.fillMaxWidth()) {
                Text(if (createAccount) "Already have an account? Sign in" else "New user? Create account")
            }
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        Button(onClick = onBack) { Text("Back") }
    }
}
