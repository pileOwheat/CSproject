package com.example.csproject;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
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
import org.json.JSONException;
import org.json.JSONObject;

public class BattleActivity extends AppCompatActivity {
    private static final String TAG = "BattleActivity";
    
    private ScrollView scrollLog;
    private TextView battleLog;
    private FrameLayout controlsContainer;
    private View viewControls, viewFightOpts, viewPartyOpts;
    private ShowdownWebSocketClient socketClient;
    private ImageView playerSprite;
    private ImageView opponentSprite;
    private FrameLayout menuFragmentContainer;
    
    // UI elements for dynamic updates
    private TextView playerPokemonInfo;
    private TextView opponentPokemonInfo;
    private ProgressBar playerHP;
    private ProgressBar opponentHP;
    
    // Battle manager for handling battle state and UI updates
    private BattleManager battleManager;

    public static boolean isMenuOpen = false;
    private boolean isFormToggleEnabled = false; // Whether form change is available
    private boolean isFormChangeActive = false; // Whether form change is toggled on
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
        
        // Initialize UI elements for dynamic updates
        playerPokemonInfo = findViewById(R.id.playerInfo);
        opponentPokemonInfo = findViewById(R.id.opponentInfo);
        playerHP = findViewById(R.id.playerHP);
        opponentHP = findViewById(R.id.opponentHP);

        viewControls  = getLayoutInflater().inflate(R.layout.controls_two_buttons, controlsContainer, false);
        viewFightOpts = getLayoutInflater().inflate(R.layout.controls_fight_options, controlsContainer, false);
        viewPartyOpts = getLayoutInflater().inflate(R.layout.controls_party_options, controlsContainer, false);

        // Initialize battle manager
        battleManager = new BattleManager(this, playerPokemonInfo, opponentPokemonInfo, playerHP, opponentHP, playerSprite, opponentSprite);
        
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
        
        // Setup form change button
        Button formChangeButton = viewFightOpts.findViewById(R.id.buttonFormChange);
        formChangeButton.setOnClickListener(v -> {
            if (isFormToggleEnabled) {
                // Toggle the form change state
                isFormChangeActive = !isFormChangeActive;
                
                // Update button text based on the new state
                JSONObject requestJson = socketClient.getLastRequestJson();
                if (requestJson != null && requestJson.has("active") && !requestJson.isNull("active")) {
                    try {
                        JSONArray active = requestJson.getJSONArray("active");
                        if (active.length() > 0) {
                            JSONObject activePokemon = active.getJSONObject(0);
                            updateFormChangeButtonText(formChangeButton, activePokemon);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error processing form change", e);
                        formChangeButton.setText(isFormChangeActive ? "Form Change ON" : "Form Change");
                    }
                } else {
                    formChangeButton.setText(isFormChangeActive ? "Form Change ON" : "Form Change");
                }
                
                // Refresh move buttons to update commands
                refreshMoveButtons();
            } else {
                Toast.makeText(this, "Form change not available for this Pokémon", Toast.LENGTH_SHORT).show();
            }
        });
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

    /**
     * Refresh the battle controls (fight and party menus)
     * Called when a battle starts or when battle data is updated
     */
    public void refreshBattleControls() {
        // Make sure controls are visible
        controlsContainer.setVisibility(View.VISIBLE);
        viewControls.setVisibility(View.VISIBLE);
        viewFightOpts.setVisibility(View.GONE);
        viewPartyOpts.setVisibility(View.GONE);
        
        // Refresh the move and switch buttons with latest data
        refreshMoveButtons();
        refreshSwitchButtons();
        
        // Reset form change button state
        Button formChangeButton = viewFightOpts.findViewById(R.id.buttonFormChange);
        formChangeButton.setText("Form Change Unavailable");
        checkFormChangeAvailability();
    }
    
    /**
     * Check if form change is available for the current active Pokémon
     */
    private void checkFormChangeAvailability() {
        JSONObject requestJson = socketClient.getLastRequestJson();
        if (requestJson == null) {
            isFormToggleEnabled = false;
            Button formChangeButton = viewFightOpts.findViewById(R.id.buttonFormChange);
            formChangeButton.setText("Form Change Unavailable");
            formChangeButton.setVisibility(View.VISIBLE);
            return;
        }
        
        try {
            // Check if we have active Pokemon data with form change options
            if (requestJson.has("active") && !requestJson.isNull("active")) {
                JSONArray active = requestJson.getJSONArray("active");
                if (active.length() > 0) {
                    JSONObject activePokemon = active.getJSONObject(0);
                    
                    // Check for terastallize specifically
                    boolean canTerastallize = false;
                    if (activePokemon.has("canTerastallize") && !activePokemon.isNull("canTerastallize")) {
                        canTerastallize = true;
                    }
                    
                    // Also check for other form changes
                    boolean canMegaEvo = false;
                    boolean canDynamax = false;
                    boolean canZMove = false;
                    if (activePokemon.has("canMegaEvo") && !activePokemon.isNull("canMegaEvo")) {
                        canMegaEvo = activePokemon.getBoolean("canMegaEvo");
                    }
                    if (activePokemon.has("canDynamax") && !activePokemon.isNull("canDynamax")) {
                        canDynamax = activePokemon.getBoolean("canDynamax");
                    }
                    if (activePokemon.has("canGigantamax") && !activePokemon.isNull("canGigantamax")) {
                        canDynamax = canDynamax || activePokemon.getBoolean("canGigantamax");
                    }
                    if (activePokemon.has("canZMove") && !activePokemon.isNull("canZMove")) {
                        canZMove = true;
                    }
                    
                    isFormToggleEnabled = canTerastallize || canMegaEvo || canDynamax || canZMove;
                    
                    // Update button text based on available form change
                    Button formChangeButton = viewFightOpts.findViewById(R.id.buttonFormChange);
                    
                    // Only update the text if form change is available
                    if (isFormToggleEnabled) {
                        updateFormChangeButtonText(formChangeButton, activePokemon);
                    } else {
                        formChangeButton.setText("Form Change Unavailable");
                    }
                    
                    // Always show the button
                    formChangeButton.setVisibility(View.VISIBLE);
                    
                    Log.d(TAG, "Form change available: " + isFormToggleEnabled);
                }
            } else {
                isFormToggleEnabled = false;
                Button formChangeButton = viewFightOpts.findViewById(R.id.buttonFormChange);
                formChangeButton.setText("Form Change Unavailable");
                formChangeButton.setVisibility(View.VISIBLE);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error checking form change availability", e);
            isFormToggleEnabled = false;
            Button formChangeButton = viewFightOpts.findViewById(R.id.buttonFormChange);
            formChangeButton.setText("Form Change Unavailable");
            formChangeButton.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Update the form change button text based on the current state
     */
    private void updateFormChangeButtonText(Button formChangeButton, JSONObject activePokemon) {
        if (isFormChangeActive) {
            // Form change is active, show "On" state
            if (activePokemon.has("canTerastallize") && !activePokemon.isNull("canTerastallize")) {
                try {
                    String teraType = activePokemon.getString("canTerastallize");
                    formChangeButton.setText("Terastallize (" + teraType + ") ON");
                } catch (JSONException e) {
                    Log.e(TAG, "Error getting terastallize type", e);
                    formChangeButton.setText("Form Change ON");
                }
            } else if (activePokemon.has("canMegaEvo") && !activePokemon.isNull("canMegaEvo")) {
                formChangeButton.setText("Mega Evolve ON");
            } else if (activePokemon.has("canDynamax") && !activePokemon.isNull("canDynamax")) {
                formChangeButton.setText("Dynamax ON");
            } else if (activePokemon.has("canGigantamax") && !activePokemon.isNull("canGigantamax")) {
                formChangeButton.setText("Gigantamax ON");
            } else if (activePokemon.has("canZMove") && !activePokemon.isNull("canZMove")) {
                formChangeButton.setText("Z Move ON");
            } else {
                formChangeButton.setText("Form Change ON");
            }
        } else {
            // Form change is not active, show normal state
            if (activePokemon.has("canTerastallize") && !activePokemon.isNull("canTerastallize")) {
                try {
                    String teraType = activePokemon.getString("canTerastallize");
                    formChangeButton.setText("Terastallize (" + teraType + ")");
                } catch (JSONException e) {
                    Log.e(TAG, "Error getting terastallize type", e);
                    formChangeButton.setText("Form Change");
                }
            } else if (activePokemon.has("canMegaEvo") && !activePokemon.isNull("canMegaEvo")) {
                formChangeButton.setText("Mega Evolve");
            } else if (activePokemon.has("canDynamax") && !activePokemon.isNull("canDynamax")) {
                formChangeButton.setText("Dynamax");
            } else if (activePokemon.has("canGigantamax") && !activePokemon.isNull("canGigantamax")) {
                formChangeButton.setText("Gigantamax");
            } else if (activePokemon.has("canZMove") && !activePokemon.isNull("canZMove")) {
                formChangeButton.setText("Z Move");
            } else {
                formChangeButton.setText("Form Change");
            }
        }
    }
    
    /**
     * Refresh the move buttons with the latest battle data
     */
    private void refreshMoveButtons() {
        JSONObject requestJson = socketClient.getLastRequestJson();
        if (requestJson == null) {
            return;
        }

        try {
            // Get active Pokemon data
            if (requestJson.has("active") && !requestJson.isNull("active")) {
                JSONArray active = requestJson.getJSONArray("active");
                if (active.length() > 0) {
                    JSONObject activePokemon = active.getJSONObject(0);

                    // Check form change availability
                    checkFormChangeAvailability();
                    
                    // Setup move buttons
                    if (activePokemon.has("moves") && !activePokemon.isNull("moves")) {
                        JSONArray moves = activePokemon.getJSONArray("moves");
                        
                        // Get move buttons
                        Button[] moveButtons = new Button[4];
                        moveButtons[0] = viewFightOpts.findViewById(R.id.move1);
                        moveButtons[1] = viewFightOpts.findViewById(R.id.move2);
                        moveButtons[2] = viewFightOpts.findViewById(R.id.move3);
                        moveButtons[3] = viewFightOpts.findViewById(R.id.move4);

                        // Update each move button
                        for (int i = 0; i < 4; i++) {
                            if (i < moves.length()) {
                                JSONObject move = moves.getJSONObject(i);
                                String moveName = "";
                                int pp = 0;
                                int maxpp = 0;
                                boolean disabled = false;
                                try {
                                    moveName = move.getString("move");
                                    pp = move.getInt("pp");
                                    maxpp = move.getInt("maxpp");
                                    disabled = move.getBoolean("disabled");
                                } catch (JSONException e) {
                                    Log.e(TAG, "Error getting move data", e);
                                }

                                // Set button text and enabled state
                                moveButtons[i].setText(moveName + "\nPP: " + pp + "/" + maxpp);
                                moveButtons[i].setEnabled(!disabled);
                                moveButtons[i].setVisibility(View.VISIBLE);

                                // Set click listener
                                final int moveIndex = i;
                                moveButtons[i].setOnClickListener(v -> {
                                    // Determine if we need to add a form change command
                                    String command = "/choose move " + (moveIndex + 1);
                                    
                                    // Add form change command if toggled
                                    if (isFormChangeActive) {
                                        if (activePokemon.has("canTerastallize") && !activePokemon.isNull("canTerastallize")) {
                                            command += " terastallize";
                                        } else if (activePokemon.has("canMegaEvo") && !activePokemon.isNull("canMegaEvo")) {
                                            command += " mega";
                                        } else if (activePokemon.has("canDynamax") && !activePokemon.isNull("canDynamax")) {
                                            command += " dynamax";
                                        } else if (activePokemon.has("canGigantamax") && !activePokemon.isNull("canGigantamax")) {
                                            command += " gigantamax";
                                        } else if (activePokemon.has("canZMove") && !activePokemon.isNull("canZMove")) {
                                            command += " zmove";
                                        }
                                    }
                                    
                                    // Send the command
                                    socketClient.send(command);
                                    
                                    // Hide fight options and show main controls
                                    viewFightOpts.setVisibility(View.GONE);
                                    viewControls.setVisibility(View.VISIBLE);
                                });
                            } else {
                                moveButtons[i].setVisibility(View.GONE);
                            }
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error refreshing move buttons", e);
        }
    }
    
    /**
     * Refresh the switch buttons with the latest data from the battle
     */
    private void refreshSwitchButtons() {
        // Get the latest request data from the socket client
        JSONObject requestJson = socketClient.getLastRequestJson();
        if (requestJson == null) {
            Log.d("BattleActivity", "No request data available for switch buttons");
            return;
        }
        
        try {
            // Check if we have team data
            if (requestJson.has("side") && !requestJson.isNull("side")) {
                JSONObject side = requestJson.getJSONObject("side");
                
                if (side.has("pokemon") && !side.isNull("pokemon")) {
                    JSONArray pokemon = side.getJSONArray("pokemon");
                    
                    // Get the switch buttons
                    Button[] switchButtons = new Button[6];
                    switchButtons[0] = viewPartyOpts.findViewById(R.id.party1);
                    switchButtons[1] = viewPartyOpts.findViewById(R.id.party2);
                    switchButtons[2] = viewPartyOpts.findViewById(R.id.party3);
                    switchButtons[3] = viewPartyOpts.findViewById(R.id.party4);
                    switchButtons[4] = viewPartyOpts.findViewById(R.id.party5);
                    switchButtons[5] = viewPartyOpts.findViewById(R.id.party6);
                    
                    // Update each switch button
                    for (int i = 0; i < switchButtons.length; i++) {
                        if (i < pokemon.length()) {
                            JSONObject poke = pokemon.getJSONObject(i);
                            String pokeName = "";
                            boolean isActive = false;
                            boolean isFainted = false;
                            try {
                                pokeName = poke.getString("ident").split(":")[1];
                                isActive = poke.getBoolean("active");
                                if (poke.has("condition") && !poke.isNull("condition")) {
                                    isFainted = poke.getString("condition").equals("0 fnt");
                                }
                            } catch (JSONException e) {
                                Log.e("BattleActivity", "Error getting Pokemon data", e);
                            }
                            
                            // Set the button text
                            switchButtons[i].setText(pokeName);
                            
                            // Disable the button if the Pokemon is active or fainted
                            switchButtons[i].setEnabled(!isActive && !isFainted);
                            
                            // Show the button
                            switchButtons[i].setVisibility(View.VISIBLE);
                            
                            // Set up the click listener
                            final int pokeIndex = i;
                            switchButtons[i].setOnClickListener(v -> {
                                socketClient.send("/switch " + (pokeIndex + 1));
                                showMainControls();
                            });
                        } else {
                            // Hide unused buttons
                            switchButtons[i].setVisibility(View.GONE);
                        }
                    }
                    
                    Log.d("BattleActivity", "Updated switch buttons with " + pokemon.length() + " Pokemon");
                }
            }
        } catch (JSONException e) {
            Log.e("BattleActivity", "Error parsing switch data", e);
        }
    }

    /**
     * Show the main battle controls
     * Makes the control container and main controls visible
     */
    public void showMainControls() {
        // Make sure the control container is visible
        if (controlsContainer != null) {
            controlsContainer.setVisibility(View.VISIBLE);
        }
        
        // Show the main controls and hide the fight/party options
        if (viewControls != null) {
            viewControls.setVisibility(View.VISIBLE);
        }
        if (viewFightOpts != null) {
            viewFightOpts.setVisibility(View.GONE);
        }
        if (viewPartyOpts != null) {
            viewPartyOpts.setVisibility(View.GONE);
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
        
        // Set the battle data callback
        socketClient.setBattleDataCallback(battleManager);
        
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
