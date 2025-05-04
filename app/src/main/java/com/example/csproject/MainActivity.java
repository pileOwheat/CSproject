package com.example.csproject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentTransaction;

public class MainActivity extends AppCompatActivity {

    private FirebaseManager firebaseManager;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before setting content view
        applyThemeFromPreferences();
        
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Close all other activities when MainActivity is opened
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0) {
            // Activity was not brought to front by the system, so it's a new instance
            // Set the intent flags to clear the task and start a new task
            Intent intent = getIntent();
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            setIntent(intent);
        }

        // Initialize Firebase Manager
        firebaseManager = FirebaseManager.getInstance();
        firebaseManager.initialize(this);
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("PokemonBattlePrefs", Context.MODE_PRIVATE);

        // Check for internet connection
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Warning: This app requires an internet connection to function properly", 
                    Toast.LENGTH_LONG).show();
        }
        
        // Check if user should be redirected to login
        if (!isUserLoggedInOrGuest()) {
            // Redirect to login screen
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            return;
        }

        // Ensure we're not in battle mode in the main menu
        SoundManager.getInstance(this).setInBattleMode(false);

        Button startButton = findViewById(R.id.buttonStartBattle);//start battle
        Button settingsButton = findViewById(R.id.buttonSettings);
        Button battleHistoryButton = findViewById(R.id.buttonBattleHistory);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //intent for startbutton(will switch to battleactivity)
        startButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, BattleActivity.class);
            startActivity(intent);
        });
        
        // Settings button
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                openSettingsFragment();
            });
        }
        
        // Battle History button
        if (battleHistoryButton != null) {
            battleHistoryButton.setOnClickListener(v -> {
                openBattleHistory();
            });
        }
    }
    
    /**
     * Check if the user is logged in or has chosen to continue as a guest
     * @return true if user is logged in or has chosen guest mode
     */
    private boolean isUserLoggedInOrGuest() {
        // Check if user is signed in with Firebase
        boolean isSignedIn = firebaseManager.isUserSignedIn();
        
        // Check if user has chosen to continue as guest
        boolean isGuestMode = sharedPreferences.getBoolean("guest_mode", false);
        
        return isSignedIn || isGuestMode;
    }
    
    /**
     * Check if network is available
     * @return true if connected to internet
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Ensure we're not in battle mode when returning to the main menu
        if (SoundManager.getInstance(this) != null) {
            SoundManager.getInstance(this).setInBattleMode(false);
        }
    }
    
    /**
     * Applies the theme based on saved preferences
     */
    private void applyThemeFromPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences("PokemonBattlePrefs", Context.MODE_PRIVATE);
        boolean isDarkMode = sharedPreferences.getBoolean("dark_mode", true);
        
        // Set the night mode without recreating the activity
        // This is only used during initial activity creation
        AppCompatDelegate.setDefaultNightMode(
            isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
    
    /**
     * Opens the settings fragment
     */
    private void openSettingsFragment() {
        SettingsFragment settingsFragment = new SettingsFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.main, settingsFragment)
                .addToBackStack(null)
                .commit();
    }
    
    /**
     * Opens the battle history screen
     * If user is not logged in, redirects to login screen first
     */
    private void openBattleHistory() {
        if (firebaseManager.isUserSignedIn()) {
            // User is signed in, show battle history fragment
            BattleHistoryFragment battleHistoryFragment = new BattleHistoryFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main, battleHistoryFragment)
                    .addToBackStack(null)
                    .commit();
        } else {
            // User is not signed in, redirect to login
            Toast.makeText(this, "Please sign in to view your battle history", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.putExtra("REDIRECT_TO_HISTORY", true);
            startActivity(intent);
        }
    }
}