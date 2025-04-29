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
        Button startButton = findViewById(R.id.buttonStartSim);
        Button joinButton = findViewById(R.id.buttonJoinRandom);

        //joinbutton functionality- joins a random gen 8 battle
        joinButton.setOnClickListener(v -> {
            if (showdownClient != null) {
                showdownClient.send("|/cmd roomlist");
            }
        });


        // Create the WebSocket client and define how to display messages
        showdownClient = new ShowdownWebSocketClient(message ->
                runOnUiThread(() -> battleLog.append("\n" + message))
        );

        startButton.setOnClickListener(v -> showdownClient.connect());

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