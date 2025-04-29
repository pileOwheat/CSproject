package com.example.csproject;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;


public class BattleActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_battle);

        View root = findViewById(R.id.rootView);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                // Handle insets if needed
                return insets;
            });
        }

        //Button for opening the menu + fix for stacking multiple menus
        ImageButton menuButton = findViewById(R.id.buttonOpenMenu);
        menuButton.setOnClickListener(v -> {
            FragmentManager fm = getSupportFragmentManager();
            Fragment existingMenu = fm.findFragmentByTag("MenuFragment");

            if (existingMenu == null) {
                fm.beginTransaction()
                        .add(android.R.id.content, new MenuFragment(), "MenuFragment")
                        .addToBackStack(null)
                        .commit();
            }
        });



    }
}