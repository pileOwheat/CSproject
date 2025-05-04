package com.example.csproject;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MenuFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MenuFragment extends Fragment {
    private LinearLayout menuSidebar;

    public MenuFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_menu, container, false);
        
        menuSidebar = view.findViewById(R.id.menuSidebar);
        
        // Apply entrance animation when the menu is created
        Animation slideIn = AnimationUtils.loadAnimation(getContext(), R.anim.slide_in_right);
        menuSidebar.startAnimation(slideIn);

        Button btnSettings = view.findViewById(R.id.buttonSettings);
        Button btnForfeit = view.findViewById(R.id.buttonForfeit);
        Button btnClose = view.findViewById(R.id.buttonCloseMenu);

        btnSettings.setOnClickListener(v -> {
            // Close this menu and open settings fragment
            closeMenuWithAnimation(() -> {
                BattleActivity.isMenuOpen = false;
                
                // Launch the settings fragment
                if (getActivity() != null) {
                    SettingsFragment settingsFragment = new SettingsFragment();
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.menuFragmentContainer, settingsFragment)
                            .addToBackStack(null)
                            .commit();
                    BattleActivity.isMenuOpen = true;
                }
            });
        });

        btnForfeit.setOnClickListener(v -> {
            // Show confirmation dialog
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getActivity());
            builder.setTitle("Forfeit Battle");
            builder.setMessage("Are you sure you want to forfeit this battle? This will count as a loss.");
            builder.setPositiveButton("Forfeit", (dialog, which) -> {
                // Close the menu first
                closeMenuWithAnimation(() -> {
                    BattleActivity.isMenuOpen = false;
                    
                    // Get the BattleActivity
                    if (getActivity() instanceof BattleActivity) {
                        BattleActivity battleActivity = (BattleActivity) getActivity();
                        
                        // Add to battle log
                        battleActivity.getBattleLog();
                        
                        // Send forfeit command through the WebSocket
                        ShowdownWebSocketClient socketClient = battleActivity.getSocketClient();
                        if (socketClient != null) {
                            socketClient.send("/forfeit");
                            
                            // Navigate back to main menu after a short delay
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                battleActivity.navigateToMainMenu();
                            }, 1500); // 1.5 second delay
                        } else {
                            // If not connected, just go back to main menu
                            Toast.makeText(getActivity(), "Connection lost. Returning to main menu.", Toast.LENGTH_SHORT).show();
                            battleActivity.navigateToMainMenu();
                        }
                    }
                });
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        });

        btnClose.setOnClickListener(v -> {
            closeMenuWithAnimation(() -> {
                BattleActivity.isMenuOpen = false;
            });
        });

        return view;
    }
    
    /**
     * Closes the menu with a slide-out animation and then performs the given action
     */
    private void closeMenuWithAnimation(Runnable onAnimationEnd) {
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
                            .remove(MenuFragment.this)
                            .commit();
                    requireActivity().getSupportFragmentManager().popBackStack();
                    
                    // Execute the callback
                    if (onAnimationEnd != null) {
                        onAnimationEnd.run();
                    }
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                // Nothing to do
            }
        });
        
        menuSidebar.startAnimation(slideOut);
    }
}