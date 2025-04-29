package com.example.csproject;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class BattleActivity extends AppCompatActivity {
    private ScrollView scrollLog;
    private TextView battleLog;
    private FrameLayout controlsContainer;
    private View viewControls;
    private View viewFightOpts, viewPartyOpts;
    private ShowdownWebSocketClient socketClient;

    public static boolean isMenuOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);

        scrollLog = findViewById(R.id.scrollLogContainer);
        battleLog = findViewById(R.id.battleLog);
        controlsContainer = findViewById(R.id.controlsContainer);

        viewControls  = getLayoutInflater().inflate(R.layout.controls_two_buttons, controlsContainer, false);
        viewFightOpts = getLayoutInflater().inflate(R.layout.controls_fight_options, controlsContainer, false);
        viewPartyOpts = getLayoutInflater().inflate(R.layout.controls_party_options, controlsContainer, false);

        controlsContainer.addView(viewControls);
        controlsContainer.addView(viewFightOpts);
        controlsContainer.addView(viewPartyOpts);

        viewControls.setVisibility(View.VISIBLE);
        viewFightOpts.setVisibility(View.GONE);
        viewPartyOpts.setVisibility(View.GONE);

        setupControls();
        setupMenuButton();
        initWebSocket();
    }

    private void setupControls() {
        Button fightBtn = viewControls.findViewById(R.id.buttonFight);
        Button partyBtn = viewControls.findViewById(R.id.buttonParty);

        fightBtn.setOnClickListener(v -> {
            viewControls.setVisibility(View.GONE);
            viewFightOpts.setVisibility(View.VISIBLE);
        });
        partyBtn.setOnClickListener(v -> {
            viewControls.setVisibility(View.GONE);
            viewPartyOpts.setVisibility(View.VISIBLE);
        });

        viewFightOpts.findViewById(R.id.buttonBackFight).setOnClickListener(v -> {
            viewFightOpts.setVisibility(View.GONE);
            viewControls.setVisibility(View.VISIBLE);
        });
        viewPartyOpts.findViewById(R.id.buttonBackParty).setOnClickListener(v -> {
            viewPartyOpts.setVisibility(View.GONE);
            viewControls.setVisibility(View.VISIBLE);
        });
    }

    private void setupMenuButton() {
        ImageButton menuBtn = findViewById(R.id.buttonOpenMenu);
        menuBtn.setOnClickListener(v -> {
            if (!isMenuOpen) showMenuFragment();
        });
    }

    private void setupMoveButtons(){

    }



    private void showMenuFragment() {
        MenuFragment menuFragment = new MenuFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        transaction.replace(R.id.menuFragmentContainer, menuFragment);
        transaction.commit();
        findViewById(R.id.menuFragmentContainer).setVisibility(View.VISIBLE);
        isMenuOpen = true;
    }

    public void closeMenuFragment() {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.menuFragmentContainer);
        if (frag != null) {
            getSupportFragmentManager().beginTransaction().remove(frag).commit();
        }
        findViewById(R.id.menuFragmentContainer).setVisibility(View.GONE);
        isMenuOpen = false;
    }

    @Override
    public void onBackPressed() {
        if (isMenuOpen) {
            closeMenuFragment();
        } else {
            super.onBackPressed();
        }
    }

    private void initWebSocket() {
        socketClient = new ShowdownWebSocketClient(message -> runOnUiThread(() -> {
            battleLog.append(message + "\n");
            scrollLog.fullScroll(View.FOCUS_DOWN);
        }));
        socketClient.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (socketClient != null) {
            socketClient.close();
        }
    }
}
