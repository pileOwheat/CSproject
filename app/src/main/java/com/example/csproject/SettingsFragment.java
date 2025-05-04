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
import com.google.firebase.auth.FirebaseUser;

public class SettingsFragment extends Fragment {
    private LinearLayout settingsContainer;
    private SwitchCompat switchDarkMode;
    private SwitchCompat switchSoundEffects;
    private SwitchCompat switchBackgroundMusic;
    private SeekBar seekBarVolume;
    private TextView textVolumeValue;
    private Button buttonTestSound;
    private ImageView btnBackFromSettings;
    private LinearLayout accountSettingsLayout;
    private TextView textViewAccountStatus;
    private Button buttonLogout;
    private Button buttonDeleteAccount;
    
    private SharedPreferences sharedPreferences;
    private SoundManager soundManager;
    private FirebaseManager firebaseManager;
    
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
        accountSettingsLayout = view.findViewById(R.id.accountSettingsLayout);
        textViewAccountStatus = view.findViewById(R.id.textViewAccountStatus);
        buttonLogout = view.findViewById(R.id.buttonLogout);
        buttonDeleteAccount = view.findViewById(R.id.buttonDeleteAccount);
        
        // Initialize sound manager and firebase manager
        soundManager = SoundManager.getInstance(requireContext());
        firebaseManager = FirebaseManager.getInstance();
        
        // Apply entrance animation
        Animation slideIn = AnimationUtils.loadAnimation(getContext(), R.anim.slide_in_right);
        settingsContainer.startAnimation(slideIn);
        
        // Load saved preferences
        loadSettings();
        
        // Set up listeners
        setupListeners();
        
        // Setup account section
        setupAccountSection();
        
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
    
    private void setupAccountSection() {
        // Check if user is signed in
        boolean isUserSignedIn = firebaseManager.isUserSignedIn();
        boolean isGuestMode = sharedPreferences.getBoolean("guest_mode", false);
        
        if (isUserSignedIn) {
            FirebaseUser currentUser = firebaseManager.getCurrentUser();
            if (currentUser != null && currentUser.getEmail() != null) {
                textViewAccountStatus.setText("Signed in as: " + currentUser.getEmail());
            } else {
                textViewAccountStatus.setText("Signed in");
            }
            
            // Show account settings
            accountSettingsLayout.setVisibility(View.VISIBLE);
            buttonLogout.setVisibility(View.VISIBLE);
            buttonDeleteAccount.setVisibility(View.VISIBLE);
        } else if (isGuestMode) {
            textViewAccountStatus.setText("Guest Mode");
            
            // Show only logout button for guest mode
            accountSettingsLayout.setVisibility(View.VISIBLE);
            buttonLogout.setVisibility(View.VISIBLE);
            buttonDeleteAccount.setVisibility(View.GONE);
        } else {
            // User is not signed in and not in guest mode
            // This shouldn't happen normally, but handle it just in case
            accountSettingsLayout.setVisibility(View.GONE);
        }
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
                soundManager.playPokemonCryByName("pikachu");
            } else {
                Toast.makeText(requireContext(), "Sound effects are disabled. Please enable them first.", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Back button listener
        btnBackFromSettings.setOnClickListener(v -> {
            closeSettingsWithAnimation();
        });
        
        // Logout button listener
        buttonLogout.setOnClickListener(v -> {
            showLogoutConfirmationDialog();
        });
        
        // Delete account button listener
        buttonDeleteAccount.setOnClickListener(v -> {
            showDeleteAccountConfirmationDialog();
        });
    }
    
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Sign out user
                    firebaseManager.signOut();
                    
                    // Clear guest mode flag
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean("guest_mode", false);
                    editor.apply();
                    
                    // Show toast
                    Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();
                    
                    // Redirect to login screen
                    Intent intent = new Intent(requireActivity(), LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                })
                .setNegativeButton("No", null)
                .show();
    }
    
    private void showDeleteAccountConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Yes, Delete My Account", (dialog, which) -> {
                    // Show a second confirmation dialog
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Confirm Deletion")
                            .setMessage("This will permanently delete your account and all associated data. Are you absolutely sure?")
                            .setPositiveButton("Yes, I'm Sure", (dialog2, which2) -> {
                                deleteUserAccount();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void deleteUserAccount() {
        FirebaseUser user = firebaseManager.getCurrentUser();
        if (user != null) {
            // Delete the user account
            user.delete()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Clear preferences
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putBoolean("guest_mode", false);
                            editor.apply();
                            
                            // Show toast
                            Toast.makeText(requireContext(), "Account deleted successfully", Toast.LENGTH_SHORT).show();
                            
                            // Redirect to login screen
                            Intent intent = new Intent(requireActivity(), LoginActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            requireActivity().finish();
                        } else {
                            // If delete fails, show error message
                            Toast.makeText(requireContext(), 
                                    "Failed to delete account: " + task.getException().getMessage(), 
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        }
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
    
    private int getThemeColor(int attribute) {
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(attribute, typedValue, true);
        return typedValue.data;
    }
    
    private void showThemeChangeDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Theme Changed")
                .setMessage("The theme has been changed. Would you like to apply it now?")
                .setPositiveButton("Apply Now", (dialog, which) -> {
                    // Apply theme change immediately
                    applyDarkModeSetting();
                    
                    // Restart the main activity to apply theme properly
                    Intent intent = new Intent(requireActivity(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                })
                .setNegativeButton("Later", null)
                .show();
    }
    
    private void closeSettingsWithAnimation() {
        // Save settings before closing
        saveSettings();
        
        // Apply exit animation
        Animation slideOut = AnimationUtils.loadAnimation(getContext(), R.anim.slide_out_right);
        settingsContainer.startAnimation(slideOut);
        
        // Wait for animation to finish before popping the fragment
        new Handler().postDelayed(() -> {
            if (isAdded()) {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        }, slideOut.getDuration());
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Save settings when fragment is paused
        saveSettings();
    }
}
