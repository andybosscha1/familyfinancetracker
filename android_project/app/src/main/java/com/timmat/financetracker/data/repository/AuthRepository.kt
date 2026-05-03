package com.timmat.financetracker.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.timmat.financetracker.R
import com.timmat.financetracker.data.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Emits the current [FirebaseUser] (or null) whenever auth state changes. */
    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Launches the Credential Manager bottom sheet, obtains a Google ID token,
     * exchanges it for a Firebase credential, and upserts the user profile.
     *
     * Throws on any failure so the ViewModel can surface the error to the UI.
     */
    suspend fun signInWithGoogle(activityContext: Context): FirebaseUser {
        val webClientId = activityContext.getString(R.string.default_web_client_id)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(activityContext)
        val response = credentialManager.getCredential(activityContext, request)

        val credential = response.credential
        val idToken = when (credential) {
            is GoogleIdTokenCredential -> credential.idToken
            else -> GoogleIdTokenCredential.createFrom(credential.data).idToken
        }

        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(firebaseCredential).await()
        val user = authResult.user
            ?: throw IllegalStateException("Firebase returned null user after sign-in")

        // Upsert user profile. Rules only allow the owner to write their own doc.
        val profile = User(
            uid = user.uid,
            email = (user.email ?: "").lowercase(),
            displayName = user.displayName ?: "",
        )
        firestore.collection("users").document(user.uid)
            .set(profile, SetOptions.merge()).await()

        return user
    }

    suspend fun signOut() {
        auth.signOut()
        runCatching {
            CredentialManager.create(context)
                .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
        }
    }
}
