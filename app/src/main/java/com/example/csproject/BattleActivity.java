package com.example.csproject;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.csproject.ShowdownWebSocketClient;

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

        // 1) Core views
        scrollLog         = findViewById(R.id.scrollLogContainer);
        battleLog         = findViewById(R.id.battleLog);
        controlsContainer = findViewById(R.id.controlsContainer);

        // 2) Spectator mode: hide the entire controls area
        boolean spectator = getIntent().getBooleanExtra("spectator_mode", false);
        if (spectator) {
            controlsContainer.setVisibility(View.GONE);
        }

        // 3) Inflate control panels (but don't attach yet)
        viewControls  = getLayoutInflater()
                .inflate(R.layout.controls_two_buttons,    controlsContainer, false);
        viewFightOpts = getLayoutInflater()
                .inflate(R.layout.controls_fight_options,  controlsContainer, false);
        viewPartyOpts = getLayoutInflater()
                .inflate(R.layout.controls_party_options,  controlsContainer, false);

        // 4) Wire up each panel before adding to the container
        wireControlPanels(viewControls, viewFightOpts, viewPartyOpts);

        // 5) Attach them (in order)
        controlsContainer.addView(viewControls);
        controlsContainer.addView(viewFightOpts);
        controlsContainer.addView(viewPartyOpts);

        // 6) Hook move and switch buttons
        setupMoveButtons();

        // 7) Menu button and WebSocket
        setupMenuButton();
        initWebSocket();
    }

    /** Wire the fight & party control panels. */
    private void wireControlPanels(
            View controls,
            View fightOpts,
            View partyOpts
    ) {
        // Main panel: Fight / Party
        Button btnFight = controls.findViewById(R.id.buttonFight);
        Button btnParty = controls.findViewById(R.id.buttonParty);

        btnFight.setOnClickListener(v -> {
            controls.setVisibility(View.GONE);
            fightOpts.setVisibility(View.VISIBLE);
        });
        btnParty.setOnClickListener(v -> {
            controls.setVisibility(View.GONE);
            partyOpts.setVisibility(View.VISIBLE);
        });

        // Fight-options Back
        Button backFight = fightOpts.findViewById(R.id.buttonBackFight);
        backFight.setOnClickListener(v -> {
            fightOpts.setVisibility(View.GONE);
            controls.setVisibility(View.VISIBLE);
        });

        // Party-options Back
        Button backParty = partyOpts.findViewById(R.id.buttonBackParty);
        backParty.setOnClickListener(v -> {
            partyOpts.setVisibility(View.GONE);
            controls.setVisibility(View.VISIBLE);
        });

        // Initial visibilities
        controls .setVisibility(View.VISIBLE);
        fightOpts.setVisibility(View.GONE);
        partyOpts.setVisibility(View.GONE);
    }

    /** Set up the four move buttons and six switch buttons. */
    private void setupMoveButtons() {
        // 1) Universal move‐click listener: reads the tag you’ll set below
        View.OnClickListener moveClick = v -> {
            int idx = Integer.parseInt(v.getTag().toString());
            socketClient.send("|/choose move " + idx);
            viewFightOpts.setVisibility(View.GONE);
            viewControls.setVisibility(View.VISIBLE);
        };

        // 2) Dynamically look up move1…move4 in your fight‐options view
        for (int i = 1; i <= 4; i++) {
            String name = "move" + i;
            int resId = getResources().getIdentifier(name, "id", getPackageName());
            Button m = viewFightOpts.findViewById(resId);
            if (m != null) {
                // ensure XML has android:tag="1", etc—or set it here:
                m.setTag(String.valueOf(i));
                m.setOnClickListener(moveClick);
            } else {
                Log.w("BattleActivity", "setupMoveButtons: no view for ID " + name);
            }
        }

        // 3) Same pattern for switch buttons: buttonSwitch1…buttonSwitch6
        View.OnClickListener switchClick = v -> {
            int idx = Integer.parseInt(v.getTag().toString());
            socketClient.send("|/choose switch " + idx);
            viewPartyOpts.setVisibility(View.GONE);
            viewControls.setVisibility(View.VISIBLE);
        };

        for (int i = 1; i <= 6; i++) {
            String name = "buttonSwitch" + i;
            int resId = getResources().getIdentifier(name, "id", getPackageName());
            Button sw = viewPartyOpts.findViewById(resId);
            if (sw != null) {
                sw.setTag(String.valueOf(i));
                sw.setOnClickListener(switchClick);
            } else {
                Log.w("BattleActivity", "setupMoveButtons: no view for ID " + name);
            }
        }
    }


    /** Menu button logic. */
    private void setupMenuButton() {
        ImageButton menuBtn = findViewById(R.id.buttonOpenMenu);
        menuBtn.setOnClickListener(v -> {
            if (!isMenuOpen) showMenuFragment();
        });
    }

    private void showMenuFragment() {
        MenuFragment menuFragment = new MenuFragment();
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        tx.replace(R.id.menuFragmentContainer, menuFragment);
        tx.commit();
        findViewById(R.id.menuFragmentContainer).setVisibility(View.VISIBLE);
        isMenuOpen = true;
    }

    public void closeMenuFragment() {
        Fragment frag = getSupportFragmentManager()
                .findFragmentById(R.id.menuFragmentContainer);
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

    /** Initialize WebSocket to append messages to the log. */
    private void initWebSocket() {
        socketClient = new ShowdownWebSocketClient(message ->
                runOnUiThread(() -> {
                    battleLog.append(message + "\n");
                    scrollLog.fullScroll(View.FOCUS_DOWN);
                })
        );
        socketClient.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (socketClient != null) socketClient.close();
    }
}

