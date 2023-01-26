package com.anselm.books.ui.sync

import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentSyncBinding
import com.anselm.books.ui.widgets.BookFragment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class SyncFragment: BookFragment() {
    private var _binding: FragmentSyncBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SyncViewModel by viewModels()
    private val signInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(requireActivity(), gso)
    }
    private val accountManager by lazy {
        AccountManager.get(requireContext())
    }

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
            binding.idSyncButton.setOnClickListener { auth() }
        }

        binding.idLogoutButton.setOnClickListener {
            signInClient.signOut().addOnCompleteListener {
                viewModel.account = null
                binding.idSyncButton.text = getString(R.string.sync_login)
                binding.idSyncButton.setOnClickListener { logIn() }
            }
            signInClient.revokeAccess().addOnCompleteListener {
                Log.d(TAG, "Fully logged out.")
            }
        }

        handleMenu()

        return binding.root
    }

    private var authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "result: $result.")
        auth(fromIntent = true)
    }

    // https://developer.android.com/training/id-auth/authenticate
    private fun auth(fromIntent: Boolean = false) {
        val account = viewModel.account!!
        val options = Bundle()
        // "https://www.googleapis.com/auth/drive.file",
        accountManager.getAuthToken(
            account,
            "oauth2: https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata",
            options,
            requireActivity(),
            object : AccountManagerCallback<Bundle> {
                override fun run(result: AccountManagerFuture<Bundle>?) {
                    if (result != null && result.result != null) {
                        val bundle = result.result
                        // If the bundle has an intent, we need to run it.
                        val intent = bundle.get(AccountManager.KEY_INTENT) as? Intent
                        if (intent != null && ! fromIntent) {
                            authLauncher.launch(intent)
                        } else {
                            withToken(bundle.getString(AccountManager.KEY_AUTHTOKEN)!!)
                        }
                    } else {
                        Log.d(TAG, "Auth failed, user feedback needed.")
                    }
                }
            },
            Looper.myLooper()?.let {
                Handler(it, object : Handler.Callback {
                    override fun handleMessage(msg: Message): Boolean {
                        Log.d(TAG, "An error occurred: $msg")
                        return true
                    }

                })
            }
        )
    }

    private fun withToken(authToken: String) {
        app.loading(true, "SyncFragment.sync")
        val syncDrive = SyncDrive(authToken)
        syncDrive.sync() {
            app.loading(false, "SyncFragment.sync")
        }
    }

    private fun logIn() {
        logInLauncher.launch(signInClient.signInIntent)
    }

    private fun handleSignData(data: Intent?) {
        GoogleSignIn.getSignedInAccountFromIntent(data)
            .addOnCompleteListener {
                if (it.isSuccessful && it.result != null) {
                    Log.d(TAG, "account ${it.result.account}")
                    Log.d(TAG, "displayName ${it.result.displayName}")
                    Log.d(TAG, "Email ${it.result.email}")
                    viewModel.account = it.result.account!!
                    binding.idSyncButton.text = getString(R.string.sync_do_sync)
                    binding.idSyncButton.setOnClickListener { auth() }
                } else {
                    // authentication failed
                    Log.e(TAG, "Failed to log in.", it.exception)
                }
            }
    }

}