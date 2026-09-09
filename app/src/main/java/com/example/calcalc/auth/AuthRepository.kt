package com.example.calcalc.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.calcalc.data.FirebaseConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Raised when the user backs out of the account picker — not an error worth showing. */
class SignInCancelled : Exception()

/**
 * Google Sign-In through Credential Manager. Google identity (rather than a username and
 * password) is what makes the data survive a reinstall or a new phone: the Firestore
 * documents are keyed by the Google account's Firebase uid.
 */
class AuthRepository(private val appContext: Context) {

    private val auth get() = Firebase.auth

    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUid: String? get() = auth.currentUser?.uid

    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener {
            trySend(it.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * @param activityContext must be an Activity — Credential Manager renders a system dialog.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser> = runCatching {
        val serverClientId = FirebaseConfig.webClientId(appContext)
            ?: error("Firebase is not configured: google-services.json is missing.")

        val credentialManager = CredentialManager.create(activityContext)

        // First pass only offers accounts already used with this app, which makes the
        // common "sign in again after reinstall" case a single tap. If there are none,
        // fall back to the full account picker.
        val credential = try {
            requestGoogleId(credentialManager, activityContext, serverClientId, filterByAuthorized = true)
        } catch (e: NoCredentialException) {
            requestGoogleId(credentialManager, activityContext, serverClientId, filterByAuthorized = false)
        }

        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCredential).await().user
            ?: error("Sign-in succeeded but returned no user.")
    }.recoverCatching { throwable ->
        if (throwable is GetCredentialCancellationException) throw SignInCancelled()
        throw throwable
    }

    private suspend fun requestGoogleId(
        credentialManager: CredentialManager,
        activityContext: Context,
        serverClientId: String,
        filterByAuthorized: Boolean,
    ): CustomCredential {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(filterByAuthorized)
            .setAutoSelectEnabled(filterByAuthorized)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = credentialManager.getCredential(activityContext, request)
        val credential = response.credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "Unexpected credential type: ${credential.type}" }
        return credential
    }

    suspend fun signOut(activityContext: Context) {
        auth.signOut()
        // Also clear the Credential Manager state, otherwise the next sign-in silently
        // auto-selects the account that was just signed out of.
        runCatching {
            CredentialManager.create(activityContext).clearCredentialState(
                androidx.credentials.ClearCredentialStateRequest(
                    androidx.credentials.ClearCredentialStateRequest.TYPE_CLEAR_CREDENTIAL_STATE
                )
            )
        }
    }
}
