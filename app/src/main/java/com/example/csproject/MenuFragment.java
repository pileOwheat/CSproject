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

        Button btnMain = view.findViewById(R.id.buttonBackToMain);
        Button btnSettings = view.findViewById(R.id.buttonSettings);
        Button btnClose = view.findViewById(R.id.buttonCloseMenu);

        btnMain.setOnClickListener(v -> {
            // Skip animation and go directly to main menu
            BattleActivity.isMenuOpen = false;
            Intent intent = new Intent(getActivity(), MainActivity.class);
            startActivity(intent);
        });

        btnSettings.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Settings coming soon!", Toast.LENGTH_SHORT).show();
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