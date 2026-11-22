package com.arnit.imagex.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun signIn(email: String, password: String): FirebaseUser =
        auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Sign-in completed without a user.")

    suspend fun register(email: String, password: String): FirebaseUser =
        auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Registration completed without a user.")

    suspend fun signOut() = auth.signOut()

    suspend fun getIdToken(forceRefresh: Boolean = false): String =
        auth.currentUser?.getIdToken(forceRefresh)?.await()?.token
            ?: error("Please sign in first.")
}
