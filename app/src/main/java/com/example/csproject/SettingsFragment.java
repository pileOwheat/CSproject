package com.example.csproject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.example.csproject.MainActivity;

public class SettingsFragment extends Fragment {
    private LinearLayout settingsContainer;
    private SwitchCompat switchDarkMode;
    private SwitchCompat switchSoundEffects;
    private SwitchCompat switchBackgroundMusic;
    private SeekBar seekBarVolume;
    private TextView textVolumeValue;
    private Button buttonTestSound;
    private ImageView btnBackFromSettings;
    
    private SharedPreferences sharedPreferences;
    private SoundManager soundManager;
    
    private boolean isDarkMode;
    private boolean isSoundEffectsEnabled;
    private boolean isBackgroundMusicEnabled;
    private int soundVolume;

    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        // Initialize views
        settingsContainer = view.findViewById(R.id.settingsContainer);
        switchDarkMode = view.findViewById(R.id.switchDarkMode);
        switchSoundEffects = view.findViewById(R.id.switchSoundEffects);
        switchBackgroundMusic = view.findViewById(R.id.switchBackgroundMusic);
        seekBarVolume = view.findViewById(R.id.seekBarVolume);
        textVolumeValue = view.findViewById(R.id.textVolumeValue);
        buttonTestSound = view.findViewById(R.id.buttonTestSound);
        btnBackFromSettings = view.findViewById(R.id.btnBackFromSettings);
        
        // Initialize sound manager
        soundManager = SoundManager.getInstance(requireContext());
        
        // Apply entrance animation
        Animation slideIn = AnimationUtils.loadAnimation(getContext(), R.anim.slide_in_right);
        settingsContainer.startAnimation(slideIn);
        
        // Load saved preferences
        loadSettings();
        
        // Set up listeners
        setupListeners();
        
        return view;
    }
    
    private void loadSettings() {
        sharedPreferences = requireActivity().getSharedPreferences("PokemonBattlePrefs", Context.MODE_PRIVATE);
        
        // Load saved settings or use defaults
        isDarkMode = sharedPreferences.getBoolean("dark_mode", false); // Default to light mode
        isSoundEffectsEnabled = sharedPreferences.getBoolean("sound_effects", true);
        isBackgroundMusicEnabled = sharedPreferences.getBoolean("background_music", true);
        soundVolume = sharedPreferences.getInt("sound_volume", 80);
        
        // Update UI to reflect current settings
        switchDarkMode.setChecked(isDarkMode);
        switchSoundEffects.setChecked(isSoundEffectsEnabled);
        switchBackgroundMusic.setChecked(isBackgroundMusicEnabled);
        seekBarVolume.setProgress(soundVolume);
        textVolumeValue.setText(soundVolume + "%");
    }
    
    private void setupListeners() {
        // Dark mode switch listener - save setting but don't apply immediately
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isDarkMode = isChecked;
            
            // Save the setting
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("dark_mode", isDarkMode);
            editor.apply();
            
            // Show dialog with options to return to main menu or stay
            showThemeChangeDialog();
        });
        
        // Sound effects switch listener
        switchSoundEffects.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isSoundEffectsEnabled = isChecked;
            // Apply sound effects setting immediately
            soundManager.setSoundEffectsEnabled(isChecked);
        });
        
        // Background music switch listener
        switchBackgroundMusic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isBackgroundMusicEnabled = isChecked;
            // Apply background music setting immediately
            soundManager.setBackgroundMusicEnabled(isChecked);
        });
        
        // Volume seekbar listener
        seekBarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                soundVolume = progress;
                textVolumeValue.setText(progress + "%");
                
                // Apply volume change immediately
                float volumeLevel = progress / 100.0f;
                soundManager.setVolume(volumeLevel);
                
                // Play a test sound when adjusting volume
                if (fromUser && isSoundEffectsEnabled && progress % 10 == 0) {
                    soundManager.playSoundEffect(SoundManager.SFX_CLICK);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not needed
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Not needed
            }
        });
        
        // Test sound button listener
        buttonTestSound.setOnClickListener(v -> {
            if (isSoundEffectsEnabled) {
                // Play Pikachu's cry immediately
                soundManager.playPokemonCry(25);
            } else {
                Toast.makeText(requireContext(), "Sound effects are disabled. Please enable them first.", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Back button listener
        btnBackFromSettings.setOnClickListener(v -> {
            closeSettingsWithAnimation();
        });
    }
    
    private void saveSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("dark_mode", isDarkMode);
        editor.putBoolean("sound_effects", isSoundEffectsEnabled);
        editor.putBoolean("background_music", isBackgroundMusicEnabled);
        editor.putInt("sound_volume", soundVolume);
        editor.apply();
    }
    
    private void applyDarkModeSetting() {
        // Apply the theme change
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        
        // Update UI elements in the settings fragment
        updateSettingsUIForTheme();
    }
    
    private void updateSettingsUIForTheme() {
        // Update UI elements in the settings fragment to reflect the theme change
        // This avoids the need to recreate the entire activity
        
        // Re-apply background colors and text colors based on current theme
        if (getContext() != null) {
            // Get theme attributes
            int backgroundColor = getThemeColor(android.R.attr.colorBackground);
            int textColor = getThemeColor(android.R.attr.textColorPrimary);
            
            // Update the settings container background
            settingsContainer.setBackgroundColor(backgroundColor);
            
            // Refresh all text colors and backgrounds as needed
            // This is a simplified example - you would need to update all relevant views
        }
    }
    
    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }
    
    private void closeSettingsWithAnimation() {
        Animation slideOut = AnimationUtils.loadAnimation(getContext(), R.anim.slide_out_right);
        slideOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                // Nothing to do
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                // Remove the fragment after the animation completes
                if (isAdded()) {
                    requireActivity().getSupportFragmentManager().beginTransaction()
                            .remove(SettingsFragment.this)
                            .commit();
                    requireActivity().getSupportFragmentManager().popBackStack();
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                // Nothing to do
            }
        });
        
        settingsContainer.startAnimation(slideOut);
    }
    
    private void showThemeChangeDialog() {
        if (getActivity() != null) {
            // Determine if we're in the main menu or not
            boolean isInMainMenu = getActivity().getClass().getSimpleName().equals("MainActivity");
            
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle("Theme Settings Changed")
                .setMessage("⚠️ IMPORTANT: You must return to the MAIN MENU for theme changes to take effect");
                
            if (isInMainMenu) {
                // If already in main menu, offer to restart
                builder.setPositiveButton("Restart Now", (dialog, which) -> {
                    // Apply theme change
                    applyDarkModeSetting();
                    
                    // Restart the activity
                    Intent intent = getActivity().getIntent();
                    getActivity().finish();
                    startActivity(intent);
                });
                builder.setNegativeButton("Later", (dialog, which) -> {
                    // Do nothing, dismiss dialog
                });
            } else {
                // If not in main menu, offer to return to main menu
                builder.setPositiveButton("Return to Main Menu", (dialog, which) -> {
                    // Return to main menu
                    Intent intent = new Intent(getActivity(), MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    getActivity().finish();
                });
                builder.setNegativeButton("Stay Here", (dialog, which) -> {
                    // Do nothing, dismiss dialog
                });
            }
            
            builder.setCancelable(false) // Force user to make a choice
                   .show();
        }
    }
    
    /**
     * Play a sequence of different sound effects to demonstrate the sound system
     */
    private void playTestSoundSequence() {
        // First play a click sound
        soundManager.playSoundEffect(SoundManager.SFX_CLICK);
        
        // Then play other sounds with delays between them
        new Handler().postDelayed(() -> {
            // Play battle start sound
            soundManager.playSoundEffect(SoundManager.SFX_BATTLE_START);
            
            // Play attack sound after a delay
            new Handler().postDelayed(() -> {
                soundManager.playSoundEffect(SoundManager.SFX_ATTACK);
                
                // Play a Pokémon cry after a delay
                new Handler().postDelayed(() -> {
                    // Play Pikachu's cry (Pokémon #25)
                    soundManager.playPokemonCry(25);
                }, 1000);
            }, 1000);
        }, 1000);
    }
}
