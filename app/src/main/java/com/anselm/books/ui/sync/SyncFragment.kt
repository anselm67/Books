package com.anselm.books.ui.sync

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.Constants
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentSyncBinding
import com.anselm.books.ui.widgets.BookFragment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlin.concurrent.thread

class SyncFragment: BookFragment() {
    private var _binding: FragmentSyncBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SyncViewModel by viewModels()

    private var logInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> handleSignData(result.data) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentSyncBinding.inflate(inflater)
        if (viewModel.account == null) {
            val googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
            if (googleAccount != null) {
                viewModel.account = googleAccount.account!!
            }
        }
        if (viewModel.account == null) {
            binding.idSyncButton.text = getString(R.string.sync_login)
            binding.idSyncButton.setOnClickListener { logIn() }
        } else {
            binding.idSyncButton.text = getString(R.string.sync_do_sync)
            binding.idSyncButton.setOnClickListener { work(viewModel.account!!) }
        }

        handleMenu()

        return binding.root
    }

    private fun work(account: Account) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account


        val drive = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        ).setApplicationName(Constants.APP_NAME).build()
        // Create a folder.
        run({
            app.loading(true, "SyncFragment.sync")
            val syncDrive = SyncDrive(drive)
            syncDrive.sync() {
                app.loading(false, "SyncFragment.sync")
            }
        }) {
            app.loading(false, "SyncFragment.sync")
            app.toast("Sync failed, retry later.")
        }
    }

    private fun logIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val signInClient = GoogleSignIn.getClient(requireActivity(), gso)
        logInLauncher.launch(signInClient.signInIntent)
    }

    private fun handleSignData(data: Intent?) {
        GoogleSignIn.getSignedInAccountFromIntent(data)
            .addOnCompleteListener {
                if (it.isSuccessful && it.result != null) {
                    Log.d(TAG, "account ${it.result.account}")
                    Log.d(TAG, "displayName ${it.result.displayName}")
                    Log.d(TAG, "Email ${it.result.email}")
                    work(it.result.account!!)
                } else {
                    // authentication failed
                    Log.e(TAG, "Failed to log in.", it.exception)
                }
            }
    }

    private var resumeBlock: (() -> Unit)? = null
    private var authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "result: $result with resumeBlock? ${resumeBlock != null}")
        // This dance allows for the invoked block to also fail.
        val block = resumeBlock
        resumeBlock = null
        block?.invoke()
    }

    private fun run(
        block: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        thread {
            try {
                block()
            } catch (e: SyncException) {
                if ((e.cause != null) && (e.cause is UserRecoverableAuthIOException)) {
                    authLauncher.launch((e.cause as UserRecoverableAuthIOException).intent)
                    return@thread
                }
                Log.e(TAG, "Failed to sync.", e)
                onError(e)
            }
        }
    }

}