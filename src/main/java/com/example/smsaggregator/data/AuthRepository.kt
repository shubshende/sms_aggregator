package com.example.smsaggregator.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Manages Google Sign-In authentication via Firebase Auth.
 * Uses the legacy GoogleSignInClient API for broad device compatibility.
 */
class AuthRepository(private val context: Context) {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    val isSignedIn: Boolean
        get() = firebaseAuth.currentUser != null

    val displayName: String
        get() = firebaseAuth.currentUser?.displayName ?: "User"

    val email: String
        get() = firebaseAuth.currentUser?.email ?: ""

    val photoUrl: String?
        get() = firebaseAuth.currentUser?.photoUrl?.toString()

    val uid: String?
        get() = firebaseAuth.currentUser?.uid

    /**
     * Creates a GoogleSignInClient for launching the sign-in intent.
     * The web client ID is automatically resolved from google-services.json.
     * Returns null if the resource is not found (missing google-services.json).
     */
    fun getGoogleSignInClient(): GoogleSignInClient? {
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId == 0) {
                Log.e("AuthRepository", "default_web_client_id not found! Is google-services.json placed correctly?")
                return null
            }
            val webClientId = context.getString(resId)
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, gso)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to create GoogleSignInClient", e)
            null
        }
    }

    /**
     * Returns the sign-in intent, or null if the client couldn't be created.
     */
    fun getSignInIntent(): Intent? {
        return getGoogleSignInClient()?.signInIntent
    }

    /**
     * Authenticates with Firebase using the Google ID token obtained from sign-in.
     */
    suspend fun firebaseAuthWithGoogle(idToken: String): FirebaseUser? {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            Log.d("AuthRepository", "Firebase auth success: ${authResult.user?.email}")
            authResult.user
        } catch (e: Exception) {
            Log.e("AuthRepository", "Firebase auth failed", e)
            null
        }
    }

    /**
     * Signs out from both Firebase and Google.
     */
    suspend fun signOut() {
        try {
            getGoogleSignInClient()?.signOut()?.await()
            firebaseAuth.signOut()
            Log.d("AuthRepository", "Sign out successful")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Sign out error", e)
        }
    }
}
