package com.anselm.books.ui.signin

import android.accounts.Account
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentGoogleSignInBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*


/**
 * Google sign in test.
 */
class GoogleSignInFragment : Fragment() {
    private var _binding: FragmentGoogleSignInBinding? = null
    private val binding get() = _binding!!
    private var client: GoogleSignInClient? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGoogleSignInBinding.inflate(inflater, container, false)
        val root: View = binding.root


        val gso = GoogleSignInOptions
            .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        client = GoogleSignIn.getClient(requireContext(), gso)

        binding.signinButton.setOnClickListener {
            Log.d(TAG, "Button signedin clicked.")
            doSignIn()
        }
        binding.signoutButton.setOnClickListener {
            Log.d(TAG, "Button signedout clicked.")
            doSignOut()
        }

        return root
    }

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .addOnCompleteListener {
                    Log.d(TAG, "isSuccessful ${it.isSuccessful}")
                    if (it.isSuccessful) {
                        Log.d(TAG, "Account ${it.result?.account}, name: ${it.result?.displayName}")
                        withAccount(it.result?.account)
                    } else {
                        Log.e(TAG, "SignIn failed.", it.exception)
                    }
                }
        }
    }

    private fun doSignIn() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null) {
            Log.d(TAG, "You're already signed in.")
            withAccount(account.account)
            return
        }
        launcher.launch(client?.signInIntent)
    }

    private fun doSignOut(){
        client?.signOut()?.addOnCompleteListener(requireActivity(), object: OnCompleteListener<Void> {
            override fun onComplete(task : Task<Void>) {
                Log.d(TAG, "You're signed out.")
            }
        })
    }

    private fun withAccount(account: Account?) {
        val credential = GoogleAccountCredential.usingOAuth2(
            requireActivity(), listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account

        val drive = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            JacksonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(getString(R.string.app_name))
        .build()

        //createGoogleFile(drive)
        //listGoogleFiles(drive)
        saveToFile(drive, "17x3jsqsrv4XnruD4dYkUFN-FhI-G0_Ib", "Hello World Content.")
    }

    private fun listGoogleFiles(drive: Drive?) {
        drive?.let {
            requireActivity().lifecycleScope.launch(Dispatchers.Default) {
                var pageToken: String? = null
                do {
                    try {
                        val result = it.files().list().apply {
                            q = "name contains 'here'"
                            spaces = "drive"
                            fields = "nextPageToken, files(id, name)"
                            pageToken = this.pageToken
                        }.execute()
                        for (file in result.files) {
                            Log.d(TAG, "name=${file.name} id=${file.id}")
                        }
                        pageToken = result.nextPageToken
                    } catch (e: UserRecoverableAuthIOException) {
                        Log.e(TAG, "Exception while using Drive, launch auth")
                        launcher.launch(e.intent)
                    }
                } while (pageToken != null)

            }
        }
    }

    private fun createGoogleFile(drive : Drive? ) {
        val metadata: File = File()
            .setParents(Collections.singletonList("root"))
            .setMimeType("text/plain")
            .setName("Untitled file here.")
        requireActivity().lifecycleScope.launch(Dispatchers.Default) {
            drive?.let {
                val googleFile: File =
                    it.files().create(metadata).execute()
                        ?: throw IOException("Null result when requesting file creation.")
                Log.d(TAG, "Created file ${googleFile.id}")
            }
        }
    }

    private fun saveToFile(drive: Drive?, fileId: String, textContent: String) {
        val metadata = File()
        // Convert content to an AbstractInputStreamContent instance.
        val contentStream = ByteArrayContent.fromString("text/plain", textContent)

        // Update the metadata and contents.
        requireActivity().lifecycleScope.launch(Dispatchers.Default) {
            drive?.let {
                it.files().update(fileId, metadata, contentStream).execute()
                Log.d(TAG, "File $fileId saved.")
            }
        }
    }
}