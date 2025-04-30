package com.example.csproject;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import org.json.JSONArray;
import org.json.JSONObject;

public class BattleActivity extends AppCompatActivity {
    private ScrollView scrollLog;
    private TextView battleLog;
    private FrameLayout controlsContainer;
    private View viewControls, viewFightOpts, viewPartyOpts;
    private ShowdownWebSocketClient socketClient;
    public static boolean isMenuOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);

        scrollLog         = findViewById(R.id.scrollLogContainer);
        battleLog         = findViewById(R.id.battleLog);
        controlsContainer = findViewById(R.id.controlsContainer);

        viewControls  = getLayoutInflater().inflate(R.layout.controls_two_buttons,   controlsContainer, false);
        viewFightOpts = getLayoutInflater().inflate(R.layout.controls_fight_options, controlsContainer, false);
        viewPartyOpts = getLayoutInflater().inflate(R.layout.controls_party_options, controlsContainer, false);

        wireControlPanels();
        controlsContainer.addView(viewControls);
        controlsContainer.addView(viewFightOpts);
        controlsContainer.addView(viewPartyOpts);

        setupMoveButtons();
        setupSwitchButtons();  // now with +1 fix
        setupMenuButton();
        initWebSocket();
    }

    private void wireControlPanels() {
        viewControls.findViewById(R.id.buttonFight).setOnClickListener(v -> {
            viewControls.setVisibility(View.GONE);
            viewFightOpts.setVisibility(View.VISIBLE);
        });
        viewControls.findViewById(R.id.buttonParty).setOnClickListener(v -> {
            viewControls.setVisibility(View.GONE);
            viewPartyOpts.setVisibility(View.VISIBLE);
            refreshSwitchButtons();
        });
        viewFightOpts.findViewById(R.id.buttonBackFight).setOnClickListener(v -> {
            viewFightOpts.setVisibility(View.GONE);
            viewControls.setVisibility(View.VISIBLE);
        });
        viewPartyOpts.findViewById(R.id.buttonBackParty).setOnClickListener(v -> {
            viewPartyOpts.setVisibility(View.GONE);
            viewControls.setVisibility(View.VISIBLE);
        });
        viewControls .setVisibility(View.VISIBLE);
        viewFightOpts.setVisibility(View.GONE);
        viewPartyOpts.setVisibility(View.GONE);
    }

    private void setupMoveButtons() {
        View.OnClickListener moveClick = v -> {
            int idx = Integer.parseInt(v.getTag().toString());
            socketClient.send("/choose move " + idx);
            viewFightOpts.setVisibility(View.GONE);
            viewControls.setVisibility(View.VISIBLE);
        };
        for (int i = 1; i <= 4; i++) {
            Button b = viewFightOpts.findViewById(
                    getResources().getIdentifier("move" + i, "id", getPackageName())
            );
            if (b != null) {
                b.setTag(String.valueOf(i));
                b.setOnClickListener(moveClick);
            }
        }
    }

    private void setupSwitchButtons() {
        View.OnClickListener switchClick = v -> {
            Object tag = v.getTag();
            if (tag == null) {
                Toast.makeText(this, "Party not loaded yet—please wait.", Toast.LENGTH_SHORT).show();
                return;
            }
            int zeroBased = Integer.parseInt(tag.toString());
            int oneBased  = zeroBased + 1;  // 🔑 convert to 1–6
            socketClient.send("/choose switch " + oneBased);
            viewPartyOpts.setVisibility(View.GONE);
            viewControls.setVisibility(View.VISIBLE);
        };
        for (int i = 1; i <= 6; i++) {
            Button b = viewPartyOpts.findViewById(
                    getResources().getIdentifier("party" + i, "id", getPackageName())
            );
            if (b != null) b.setOnClickListener(switchClick);
        }
    }

    private void refreshSwitchButtons() {
        JSONObject req = socketClient.getLastRequestJson();
        if (req == null) return;
        try {
            String activeName = null;
            if (req.has("side")) {
                JSONObject side = req.getJSONObject("side");
                if (side.has("active")) {
                    JSONArray act = side.getJSONArray("active");
                    if (act.length() > 0) {
                        activeName = act.getJSONObject(0)
                                .getString("ident").split(",")[0]
                                .replaceAll("p\\d[a]?: ?", "").trim();
                    }
                }
            }

            JSONArray party = req.getJSONObject("side").getJSONArray("pokemon");
            for (int i = 0; i < 6; i++) {
                JSONObject mon = party.getJSONObject(i);
                String name = mon.getString("ident").split(",")[0]
                        .replaceAll("p\\d[a]?: ?", "").trim();
                boolean fainted   = mon.optString("condition","").startsWith("0");
                boolean isCurrent = name.equals(activeName);

                Button b = viewPartyOpts.findViewById(
                        getResources().getIdentifier("party" + (i+1), "id", getPackageName())
                );
                if (b != null) {
                    b.setText(name + (isCurrent ? " (current)" : ""));
                    b.setEnabled(!fainted && !isCurrent);
                    b.setTag(String.valueOf(i));
                }
            }
        } catch (Exception ignored) {}
    }

    private void setupMenuButton() {
        ImageButton mb = findViewById(R.id.buttonOpenMenu);
        mb.setOnClickListener(v -> {
            if (!isMenuOpen) {
                MenuFragment mf = new MenuFragment();
                FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
                tx.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
                tx.replace(R.id.menuFragmentContainer, mf).commit();
                findViewById(R.id.menuFragmentContainer).setVisibility(View.VISIBLE);
                isMenuOpen = true;
            }
        });
    }

    private void initWebSocket() {
        socketClient = new ShowdownWebSocketClient(msg -> runOnUiThread(() -> {
            battleLog.append(msg + "\n");
            scrollLog.fullScroll(View.FOCUS_DOWN);
        }));
        socketClient.connect();
    }

    @Override
    public void onBackPressed() {
        if (isMenuOpen) {
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.menuFragmentContainer);
            if (f != null) getSupportFragmentManager().beginTransaction().remove(f).commit();
            findViewById(R.id.menuFragmentContainer).setVisibility(View.GONE);
            isMenuOpen = false;
        } else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        socketClient.close();
    }
}
