package com.example.csproject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before setting content view
        applyThemeFromPreferences();
        
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Ensure we're not in battle mode in the main menu
        SoundManager.getInstance(this).setInBattleMode(false);

        Button startButton = findViewById(R.id.buttonStartBattle);//start battle
        Button settingsButton = findViewById(R.id.buttonSettings);

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
}