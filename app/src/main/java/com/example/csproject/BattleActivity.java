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
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class BattleActivity extends AppCompatActivity {
    private ScrollView scrollLog;
    private TextView battleLog;
    private FrameLayout controlsContainer;
    private View viewControls;
    private View viewFightOpts, viewPartyOpts;
    private WebSocket socket;

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
        if (frag !=
                null) {
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
        OkHttpClient client = new OkHttpClient.Builder()
                .hostnameVerifier((hostname, session) -> hostname.equals("sim3.psim.us"))
                .build();
        Request request = new Request.Builder()
                .url("wss://sim3.psim.us/showdown/websocket")
                .build();
        socket = client.newWebSocket(request, new ShowdownWebSocketListener());
    }

    private class ShowdownWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, okhttp3.Response response) {
            webSocket.send("|/utm randomBattle," + getTeamExport() + "\n");
            webSocket.send("|/search randomBattle\n");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            runOnUiThread(() -> battleLog.append(text + "\n"));
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
            runOnUiThread(() -> battleLog.append("Error: " + t.getMessage()));
        }
    }

    private String getTeamExport() {
        return "<YOUR SHOWDOWN TEAM TEXT>";
    }
}