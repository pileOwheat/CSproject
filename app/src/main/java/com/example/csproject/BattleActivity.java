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

        // Bind UI
        scrollLog = findViewById(R.id.scrollLogContainer);
        battleLog = findViewById(R.id.battleLog);
        controlsContainer = findViewById(R.id.controlsContainer);

        // Inflate control panels
        viewControls  = getLayoutInflater().inflate(R.layout.controls_two_buttons, controlsContainer, false);
        viewFightOpts = getLayoutInflater().inflate(R.layout.controls_fight_options, controlsContainer, false);
        viewPartyOpts = getLayoutInflater().inflate(R.layout.controls_party_options, controlsContainer, false);

        wireControlPanels();
        controlsContainer.addView(viewControls);
        controlsContainer.addView(viewFightOpts);
        controlsContainer.addView(viewPartyOpts);

        setupMoveButtons();
        setupSwitchButtons();
        setupMenuButton();
        initWebSocket();
    }

    private void wireControlPanels() {
        viewControls.findViewById(R.id.buttonFight).setOnClickListener(v -> {
            viewControls.setVisibility(View.GONE);
            viewFightOpts.setVisibility(View.VISIBLE);
            refreshMoveButtons();  // Refresh when opening Fight panel
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

        viewControls.setVisibility(View.VISIBLE);
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
                b.setTag(String.valueOf(i));  // now 1-based indexing
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
            int oneBased = zeroBased + 1;
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
            JSONObject side = req.getJSONObject("side");
            JSONArray party = side.getJSONArray("pokemon");
            String activeName = null;
            JSONArray activeArr = side.optJSONArray("active");
            if (activeArr != null && activeArr.length() > 0) {
                activeName = activeArr.getJSONObject(0)
                        .getString("ident").split(",")[0]
                        .replaceAll("p\\d[a]?: ?", "").trim();
            }
            for (int i = 0; i < party.length(); i++) {
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

    private void refreshMoveButtons() {
        JSONObject req = socketClient.getLastRequestJson();
        if (req == null) return;
        try {
            JSONArray activeArr = req.optJSONArray("active");
            if (activeArr == null || activeArr.length() == 0) return;
            JSONObject active = activeArr.getJSONObject(0);

            JSONArray moveObjs = active.optJSONArray("moves");
            for (int i = 0; i < 4; i++) {
                Button mvBtn = viewFightOpts.findViewById(
                        getResources().getIdentifier("move" + (i + 1), "id", getPackageName())
                );
                if (moveObjs != null && i < moveObjs.length()) {
                    String moveName = moveObjs.getJSONObject(i).getString("move");
                    mvBtn.setText(moveName);
                    mvBtn.setEnabled(true);
                } else {
                    mvBtn.setText("—");
                    mvBtn.setEnabled(false);
                }
            }

            Button formChange = viewFightOpts.findViewById(R.id.buttonFormChange);
            if (active.has("canMegaEvo")) {
                final int megaIndex = active.getInt("canMegaEvo");
                formChange.setEnabled(true);
                formChange.setOnClickListener(v -> {
                    socketClient.send("/choose move " + megaIndex + " mega");
                    viewFightOpts.setVisibility(View.GONE);
                    viewControls.setVisibility(View.VISIBLE);
                });
            } else {
                formChange.setEnabled(false);
                formChange.setOnClickListener(null);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupMenuButton() {
        ImageButton mb = findViewById(R.id.buttonOpenMenu);
        mb.setOnClickListener(v -> {
            if (!isMenuOpen) {
                FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
                tx.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.menuFragmentContainer, new MenuFragment())
                        .commit();
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
            getSupportFragmentManager().beginTransaction()
                    .remove(getSupportFragmentManager().findFragmentById(R.id.menuFragmentContainer))
                    .commit();
            findViewById(R.id.menuFragmentContainer).setVisibility(View.GONE);
            isMenuOpen = false;
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        socketClient.close();
    }
}
