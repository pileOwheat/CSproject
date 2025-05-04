package com.example.csproject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText emailEditText, passwordEditText;
    private Button loginButton, registerButton, guestButton;
    private ProgressBar progressBar;
    private FirebaseManager firebaseManager;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase manager
        firebaseManager = FirebaseManager.getInstance();
        firebaseManager.initialize(this);
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("PokemonBattlePrefs", Context.MODE_PRIVATE);

        // Check if user is already signed in
        if (firebaseManager.isUserSignedIn() || sharedPreferences.getBoolean("guest_mode", false)) {
            navigateToMainActivity();
            return;
        }

        // Initialize UI elements
        emailEditText = findViewById(R.id.editTextEmail);
        passwordEditText = findViewById(R.id.editTextPassword);
        loginButton = findViewById(R.id.buttonLogin);
        registerButton = findViewById(R.id.buttonRegister);
        guestButton = findViewById(R.id.buttonGuest);
        progressBar = findViewById(R.id.progressBar);

        // Check if we should redirect to battle history after login
        boolean redirectToHistory = getIntent().getBooleanExtra("REDIRECT_TO_HISTORY", false);

        // Set up button click listeners
        loginButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (validateInput(email, password)) {
                progressBar.setVisibility(View.VISIBLE);
                firebaseManager.signIn(email, password, new FirebaseManager.AuthCallback() {
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                        
                        // Clear guest mode flag when user logs in
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean("guest_mode", false);
                        editor.apply();
                        
                        // Redirect based on intent
                        if (redirectToHistory) {
                            // Go back to MainActivity which will then show battle history
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            // Just go back to MainActivity
                            navigateToMainActivity();
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(LoginActivity.this, "Login failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        registerButton.setOnClickListener(v -> navigateToRegister());
        guestButton.setOnClickListener(v -> continueAsGuest());
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private boolean validateInput(String email, String password) {
        // Validate input
        if (email.isEmpty()) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return false;
        }

        if (password.isEmpty()) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return false;
        }

        return true;
    }

    private void navigateToRegister() {
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
    }

    private void continueAsGuest() {
        // Sign out any existing user
        firebaseManager.signOut();
        
        // Set guest mode flag
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("guest_mode", true);
        editor.apply();
        
        // Navigate to main activity
        navigateToMainActivity();
        Toast.makeText(this, "Continuing as guest. Battle history will not be saved.", Toast.LENGTH_LONG).show();
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
