package com.example.csproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        Button startButton = findViewById(R.id.buttonStartBattle);//start battle

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

        //intent for join spectator button
        Button joinSpectatorButton = findViewById(R.id.buttonJoinSpectator);
        joinSpectatorButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BattleActivity.class);
            intent.putExtra("spectator_mode", true); // flag that will be checked in battleActivity
            startActivity(intent);
        });




    }
}