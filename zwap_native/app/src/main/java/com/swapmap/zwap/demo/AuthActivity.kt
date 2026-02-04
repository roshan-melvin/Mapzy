package com.swapmap.zwap.demo

import com.swapmap.zwap.R
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.api.ApiException

class AuthActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var isLoginMode = true

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: Exception) {
                Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        try {
            auth = FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Toast.makeText(this, "Firebase not initialized.", Toast.LENGTH_LONG).show()
        }

        val etEmail = findViewById<TextInputEditText>(R.id.et_email)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)
        val btnAction = findViewById<MaterialButton>(R.id.btn_action)
        val btnGoogle = findViewById<MaterialButton>(R.id.btn_google)
        val tvToggle = findViewById<android.widget.TextView>(R.id.tv_toggle)
        val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
        val tvSubtitle = findViewById<android.widget.TextView>(R.id.tv_subtitle)

        btnAction.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!this::auth.isInitialized) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return@setOnClickListener
            }

            if (isLoginMode) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            createUserProfile(auth.currentUser)
                        } else {
                            Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            createUserProfile(auth.currentUser)
                        } else {
                            Toast.makeText(this, "Sign up failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
        
        btnGoogle.setOnClickListener {
            try {
                // Try to get standard ID, fallback to specific string or handle error
                val clientId = getString(R.string.default_web_client_id)
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(clientId)
                    .requestEmail()
                    .build()
                val googleSignInClient = GoogleSignIn.getClient(this, gso)
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Configuration Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        tvToggle.setOnClickListener {
            isLoginMode = !isLoginMode
            if (isLoginMode) {
                tvTitle.text = "Welcome Back"
                tvSubtitle.text = "Sign in to continue"
                btnAction.text = "SIGN IN"
                tvToggle.text = "Don't have an account? Sign Up"
            } else {
                tvTitle.text = "Create Account"
                tvSubtitle.text = "Sign up to get started"
                btnAction.text = "SIGN UP"
                tvToggle.text = "Already have an account? Sign In"
            }
        }
    }
    
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    createUserProfile(auth.currentUser)
                } else {
                    Toast.makeText(this, "Google Auth failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    private fun createUserProfile(user: com.google.firebase.auth.FirebaseUser?) {
        if (user == null) return

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(user.uid)

        userRef.get().addOnSuccessListener { document ->
            if (!document.exists()) {
                val userData = hashMapOf(
                    "name" to (user.displayName ?: "User"),
                    "email" to (user.email ?: ""),
                    "photoURL" to (user.photoUrl?.toString() ?: ""),
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "lastLogin" to com.google.firebase.Timestamp.now(),
                    "chat_regions" to emptyList<String>(),
                    "fcmToken" to ""
                )

                userRef.set(userData)
                    .addOnSuccessListener {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to create profile", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // Update last login
                userRef.update("lastLogin", com.google.firebase.Timestamp.now())
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }.addOnFailureListener {
            // If get fails (e.g. offline), just proceed
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
    
    public override fun onStart() {
        super.onStart()
        try {
            if (this::auth.isInitialized) {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    // Temporarily just start main activity to avoid blocking if network fails
                    // In real app, we might want to ensure profile exists here too
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
