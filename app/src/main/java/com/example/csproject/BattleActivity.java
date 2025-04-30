package com.example.csproject;

import android.os.Bundle;
import android.util.Log;
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

    private boolean isFormToggleEnabled = false;
    private String currentFormType = null;

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
            refreshMoveButtons();
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
                boolean fainted = mon.optString("condition", "").startsWith("0");
                boolean isCurrent = name.equals(activeName);
                Button b = viewPartyOpts.findViewById(
                        getResources().getIdentifier("party" + (i + 1), "id", getPackageName())
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

            Log.d("BATTLE_JSON", "Active JSON: " + active.toString());

            JSONArray moveObjs = active.optJSONArray("moves");
            boolean canMega = active.optBoolean("canMegaEvo", false);
            boolean canZMove = active.optBoolean("canZMove", false);
            boolean canDynamax = active.optBoolean("canDynamax", false);
            boolean canTera = active.optBoolean("canTerastallize", false);

            // Priority: Z > Mega > Dynamax > Tera
            if (canZMove) currentFormType = "zmove";
            else if (canMega) currentFormType = "mega";
            else if (canDynamax) currentFormType = "dynamax";
            else if (canTera) currentFormType = "terastallize";
            else currentFormType = null;

            for (int i = 0; i < 4; i++) {
                Button mvBtn = viewFightOpts.findViewById(
                        getResources().getIdentifier("move" + (i + 1), "id", getPackageName())
                );
                if (moveObjs != null && i < moveObjs.length()) {
                    JSONObject moveObj = moveObjs.getJSONObject(i);
                    String moveName = moveObj.getString("move");
                    boolean isZ = moveObj.optBoolean("zMove", false);
                    mvBtn.setText(isZ ? moveName + " (Z)" : moveName);
                    mvBtn.setEnabled(true);
                    int moveIndex = i + 1;

                    mvBtn.setOnClickListener(v -> {
                        StringBuilder command = new StringBuilder("/choose move " + moveIndex);
                        if (isFormToggleEnabled) {
                            if ("zmove".equals(currentFormType) && isZ) {
                                command.append(" zmove");
                            } else if (!"zmove".equals(currentFormType)) {
                                command.append(" ").append(currentFormType);
                            }
                        }

                        socketClient.send(command.toString());
                        isFormToggleEnabled = false; // reset after use
                        viewFightOpts.setVisibility(View.GONE);
                        viewControls.setVisibility(View.VISIBLE);
                    });

                } else {
                    mvBtn.setText("—");
                    mvBtn.setEnabled(false);
                    mvBtn.setOnClickListener(null);
                }
            }

            Button formChange = viewFightOpts.findViewById(R.id.buttonFormChange);
            formChange.setEnabled(false);
            formChange.setOnClickListener(null);

            if (active.has("canMegaEvo")) {
                currentFormType = "mega";
            } else if (active.has("canZMove")) {
                currentFormType = "zmove";
            } else if (active.has("canDynamax")) {
                currentFormType = "dynamax";
            } else if (active.has("canTerastallize")) {
                currentFormType = "terastallize";
            }
            if (currentFormType != null) {
                formChange.setEnabled(true);
                formChange.setText(isFormToggleEnabled ? currentFormType.toUpperCase() + ": ON" : currentFormType.toUpperCase() + ": OFF");
                formChange.setOnClickListener(v -> {
                    isFormToggleEnabled = !isFormToggleEnabled;
                    formChange.setText(isFormToggleEnabled ? currentFormType.toUpperCase() + ": ON" : currentFormType.toUpperCase() + ": OFF");
                });
            } else {
                formChange.setEnabled(false);
                formChange.setText("Form: —");
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
