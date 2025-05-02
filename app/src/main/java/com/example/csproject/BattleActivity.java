package com.example.csproject;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

public class BattleActivity extends AppCompatActivity {
    private ScrollView scrollLog;
    private TextView battleLog;
    private FrameLayout controlsContainer;
    private View viewControls, viewFightOpts, viewPartyOpts;
    private ShowdownWebSocketClient socketClient;
    private ImageView playerSprite;
    private ImageView opponentSprite;
    private FrameLayout menuFragmentContainer;

    public static boolean isMenuOpen = false;
    private boolean isFormToggleEnabled = false;
    private String currentFormType = null;
    private String opponentSwitchInName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);

        scrollLog = findViewById(R.id.scrollLogContainer);
        battleLog = findViewById(R.id.battleLog);
        controlsContainer = findViewById(R.id.controlsContainer);
        playerSprite = findViewById(R.id.playerSprite);
        opponentSprite = findViewById(R.id.opponentSprite);
        menuFragmentContainer = findViewById(R.id.menuFragmentContainer);

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

        Button backFightButton = viewFightOpts.findViewById(R.id.buttonBackFight);
        backFightButton.setOnClickListener(v -> {
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

            JSONArray moveObjs = active.optJSONArray("moves");
            boolean canMega = active.optBoolean("canMegaEvo", false);
            boolean canZMove = active.optBoolean("canZMove", false);
            boolean canDynamax = active.optBoolean("canDynamax", false);
            boolean canTera = active.optBoolean("canTerastallize", false);

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
                        isFormToggleEnabled = false;
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
            String teraType = null;

            if (active.has("canMegaEvo")) currentFormType = "mega";
            else if (active.has("canZMove")) currentFormType = "zmove";
            else if (active.has("canDynamax")) currentFormType = "dynamax";
            else if (active.has("canTerastallize")) {
                currentFormType = "terastallize";
                teraType = active.optString("canTerastallize", null);
            }

            if (currentFormType != null) {
                String label = "Use " + currentFormType.substring(0, 1).toUpperCase() + currentFormType.substring(1);
                if ("terastallize".equals(currentFormType) && teraType != null) {
                    label += " (" + teraType + ")";
                }
                formChange.setText(label);
                formChange.setEnabled(true);
                String currentTeraType = teraType;
                formChange.setOnClickListener(v -> {
                    isFormToggleEnabled = !isFormToggleEnabled;
                    formChange.setText(isFormToggleEnabled
                            ? currentFormType.toUpperCase() + " (" + currentTeraType + "): ON"
                            : currentFormType.toUpperCase() + " (" + currentTeraType + "): OFF");
                });
            } else {
                formChange.setEnabled(false);
                formChange.setText("Form Change Unavailable");
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
                // Create and add the menu fragment as a sliding panel
                FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
                tx.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                        .replace(R.id.menuFragmentContainer, new MenuFragment())
                        .commit();
                
                // Make the menu container visible
                menuFragmentContainer.setVisibility(View.VISIBLE);
                isMenuOpen = true;
            } else {
                // Hide the menu if it's already open
                menuFragmentContainer.setVisibility(View.GONE);
                isMenuOpen = false;
            }
        });
    }

    private void initWebSocket() {
        socketClient = new ShowdownWebSocketClient(msg -> runOnUiThread(() -> {
            battleLog.append(msg + "\n");
            scrollLog.fullScroll(View.FOCUS_DOWN);
            
            // Try to load Pokemon sprites when they're mentioned
            if (msg.contains("switched in")) {
                String pokemonName = extractPokemonName(msg);
                if (pokemonName != null) {
                    if (msg.contains("You switched in")) {
                        loadPokemonSprite(pokemonName, true);
                    } else if (msg.contains("Opponent switched in")) {
                        loadPokemonSprite(pokemonName, false);
                    }
                }
            }
        }));
        socketClient.connect();
    }
    
    private String extractPokemonName(String message) {
        // Extract Pokemon name from battle log message
        if (message.contains("switched in")) {
            int startIndex = message.indexOf("switched in") + "switched in".length();
            String pokemonName = message.substring(startIndex).trim();
            // Remove any trailing characters like "!"
            if (pokemonName.endsWith("!")) {
                pokemonName = pokemonName.substring(0, pokemonName.length() - 1);
            }
            return pokemonName;
        }
        return null;
    }
    
    private void loadPokemonSprite(String pokemonName, boolean isPlayer) {
        if (pokemonName == null || pokemonName.isEmpty()) return;
        
        // Format the Pokemon name for the URL (lowercase, remove spaces and special chars)
        String formattedName = pokemonName.toLowerCase().replaceAll("[^a-z0-9]", "");
        
        // Try loading animated sprite first
        String animatedUrl = isPlayer 
            ? "https://play.pokemonshowdown.com/sprites/ani-back/" + formattedName + ".gif"
            : "https://play.pokemonshowdown.com/sprites/ani/" + formattedName + ".gif";
            
        // Fallback to static sprite if animated fails
        String staticUrl = isPlayer
            ? "https://play.pokemonshowdown.com/sprites/gen5-back/" + formattedName + ".png"
            : "https://play.pokemonshowdown.com/sprites/gen5/" + formattedName + ".png";
        
        Log.d("BattleActivity", "Attempting to load sprite for: " + pokemonName);
        
        // Try to load animated sprite with fallback to static
        Glide.with(this)
            .load(animatedUrl)
            .error(Glide.with(this).load(staticUrl))
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(isPlayer ? playerSprite : opponentSprite);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        socketClient.close();
    }
}
