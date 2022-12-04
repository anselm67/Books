package com.anselm.books.ui.signin

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentGoogleSignInBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task

/**
 * Google sign in test.
 */
class GoogleSignInFragment : Fragment() {
    private var _binding: FragmentGoogleSignInBinding? = null
    private val binding get() = _binding!!
    private var client: GoogleSignInClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

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
            return
        }
        launcher.launch(client?.signInIntent)
    }

    fun doSignOut(){
        client?.signOut()?.addOnCompleteListener(requireActivity(), object: OnCompleteListener<Void> {
            override fun onComplete(task : Task<Void>) {
                Log.d(TAG, "You're signed out.")
            }
        })
    }
}