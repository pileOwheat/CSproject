package com.example.csproject;

import android.content.Intent;
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

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;


public class BattleActivity extends AppCompatActivity {

    TextView battleLog;
    ShowdownWebSocketClient showdownClient;


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (showdownClient != null) {
            showdownClient.close();
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_battle);

        battleLog = findViewById(R.id.battleLog);



        // Create the WebSocket client and define how to display messages
        showdownClient = new ShowdownWebSocketClient(message ->
                runOnUiThread(() -> battleLog.append("\n" + message))
        );

        // Connect to Pokémon Showdown to the client
        showdownClient.connect();

        // Check if we're in spectator mode to know what to request from the showdown api.
        Intent intent = getIntent();
        boolean spectatorMode = intent.getBooleanExtra("spectator_mode", false);

        if (spectatorMode) {
            showdownClient.send("|/cmd roomlist"); // immediately triggers auto-join logic
        }


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