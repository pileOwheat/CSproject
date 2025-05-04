package com.example.csproject;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.request.target.Target;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BattleActivity extends AppCompatActivity implements ShowdownWebSocketClient.BattleDataCallback {
    private static final String TAG = "BattleActivity";
    
    private ScrollView scrollLog;
    private TextView battleLog;
    private FrameLayout controlsContainer;
    private View viewControls, viewFightOpts, viewPartyOpts;
    private ShowdownWebSocketClient socketClient;
    private ImageView playerSprite;
    private ImageView opponentSprite;
    private FrameLayout menuFragmentContainer;
    private ImageView battleBackground;
    private static final String[] BATTLE_BACKGROUNDS = {
            "bg-beach", "bg-city", "bg-dampcave", "bg-darkbeach", "bg-darkcity", 
            "bg-darkmeadow", "bg-deepsea", "bg-desert", "bg-earthycave", 
            "bg-forest", "bg-icecave", "bg-library", "bg-meadow", 
            "bg-orasdesert", "bg-orassea", "bg-skypillar"
    };
    
    // UI elements for dynamic updates
    private TextView playerPokemonInfo;
    private TextView opponentPokemonInfo;
    private ProgressBar playerHP;
    private ProgressBar opponentHP;
    
    // Battle manager for handling battle state and UI updates
    private BattleManager battleManager;
    private BattleWaitingOverlay waitingOverlay;
    private BattleFindingOverlay findingOverlay;

    public static boolean isMenuOpen = false;
    private boolean isFormToggleEnabled = false; // Whether form change is available
    private boolean isFormChangeActive = false; // Whether form change is toggled on
    private String opponentSwitchInName = null;

    // Flag to track if navigation to main menu is already in progress
    private boolean isNavigatingToMainMenu = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before super.onCreate
        applyThemeFromPreferences();
        
        super.onCreate(savedInstanceState);
        
        // Enable immersive game mode to hide system UI
        enableGameMode();
        
        setContentView(R.layout.activity_battle);

        scrollLog = findViewById(R.id.scrollLogContainer);
        battleLog = findViewById(R.id.battleLog);
        controlsContainer = findViewById(R.id.controlsContainer);
        playerSprite = findViewById(R.id.playerSprite);
        opponentSprite = findViewById(R.id.opponentSprite);
        menuFragmentContainer = findViewById(R.id.menuFragmentContainer);
        battleBackground = findViewById(R.id.battleBackground);
        
        // Load a random battle background
        loadRandomBattleBackground();
        
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
        
        // Add control views to container
        controlsContainer.addView(viewControls);
        controlsContainer.addView(viewFightOpts);
        controlsContainer.addView(viewPartyOpts);
        
        // Initialize waiting overlay - do this last so it's on top of other views
        waitingOverlay = new BattleWaitingOverlay(this, controlsContainer, v -> cancelWaitingAction());
        
        // Initialize finding overlay - no cancel button anymore
        findingOverlay = new BattleFindingOverlay(this, controlsContainer);
        
        wireControlPanels();
        setupMoveButtons();
        setupSwitchButtons();
        setupMenuButton();
        initWebSocket();
        
        // Set battle mode in SoundManager
        SoundManager.getInstance(this).setInBattleMode(true);
    }
    
    /**
     * Applies the theme based on saved preferences
     */
    private void applyThemeFromPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences("PokemonBattlePrefs", Context.MODE_PRIVATE);
        boolean isDarkMode = sharedPreferences.getBoolean("dark_mode", false);
        
        // Set the night mode without recreating the activity
        // This is only used during initial activity creation
        AppCompatDelegate.setDefaultNightMode(
            isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
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
            String moveText = ((Button)v).getText().toString().split("\n")[0];
            
            // Log this action
            Log.d(TAG, "Move selected: " + moveText + " (index: " + idx + ")");
            
            // Show waiting overlay BEFORE sending the command
            showWaitingOverlay("You chose: " + moveText);
            
            // Send the command to the server
            socketClient.send("/choose move " + idx);
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
            String pokemonName = ((Button)v).getText().toString();
            
            // Log this action
            Log.d(TAG, "Switch selected: " + pokemonName + " (index: " + oneBased + ")");
            
            // Show waiting overlay BEFORE sending the command
            showWaitingOverlay("You switched to: " + pokemonName);
            
            // Send the command to the server
            socketClient.send("/choose switch " + oneBased);
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
        // Hide waiting overlay if it's showing
        hideWaitingOverlay();
        
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
                                final String finalMoveName = moveName;
                                moveButtons[i].setOnClickListener(v -> {
                                    // Determine if we need to add a form change command
                                    String command = "/choose move " + (moveIndex + 1);
                                    
                                    // Add form change command if toggled
                                    if (isFormChangeActive) {
                                        if (activePokemon.has("canTerastallize") && !activePokemon.isNull("canTerastallize")) {
                                            command += " terastallize";
                                            Log.d(TAG, "Adding terastallize to command: " + command);
                                        } else if (activePokemon.has("canMegaEvo") && !activePokemon.isNull("canMegaEvo")) {
                                            command += " mega";
                                            Log.d(TAG, "Adding mega evolution to command: " + command);
                                        } else if (activePokemon.has("canDynamax") && !activePokemon.isNull("canDynamax")) {
                                            command += " dynamax";
                                            Log.d(TAG, "Adding dynamax to command: " + command);
                                        } else if (activePokemon.has("canGigantamax") && !activePokemon.isNull("canGigantamax")) {
                                            command += " gigantamax";
                                            Log.d(TAG, "Adding gigantamax to command: " + command);
                                        } else if (activePokemon.has("canZMove") && !activePokemon.isNull("canZMove")) {
                                            command += " zmove";
                                            Log.d(TAG, "Adding Z-Move to command: " + command);
                                        }
                                    }
                                    
                                    // Show waiting overlay BEFORE sending the command
                                    showWaitingOverlay("You chose: " + finalMoveName);
                                    
                                    // Send the command
                                    socketClient.send(command);
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
        JSONObject requestJson = socketClient.getLastRequestJson();
        if (requestJson == null) {
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
                    
                    // Check if we're waiting for an opponent
                    boolean isWaitingForOpponent = socketClient.isWaitingForOpponent();
                    
                    // Log the waiting state for debugging
                    Log.d(TAG, "Waiting for opponent: " + isWaitingForOpponent);
                    
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
                            
                            // Set the button text with level information or "Waiting for Opponent" if waiting
                            if (isWaitingForOpponent) {
                                // When waiting for opponent, show "Waiting for Opponent" instead of level
                                switchButtons[i].setText(pokeName + " (Waiting for Opponent)");
                            } else {
                                // Normal display with level information
                                int level = 100; // Default level
                                
                                // Extract level from details if available
                                if (poke.has("details") && !poke.isNull("details")) {
                                    String details = poke.getString("details");
                                    if (details.contains(", L")) {
                                        try {
                                            int levelIndex = details.indexOf(", L") + 3;
                                            int endIndex = details.indexOf(",", levelIndex);
                                            if (endIndex == -1) endIndex = details.length();
                                            String levelStr = details.substring(levelIndex, endIndex);
                                            level = Integer.parseInt(levelStr);
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error parsing level from details: " + details, e);
                                        }
                                    }
                                }
                                
                                // Set the button text with the level information
                                if (isActive) {
                                    switchButtons[i].setText(pokeName + " Lv." + level + " (active)");
                                } else {
                                    switchButtons[i].setText(pokeName + " Lv." + level);
                                }
                            }
                            
                            // Disable the button if the Pokemon is active or fainted
                            switchButtons[i].setEnabled(!isActive && !isFainted);
                            
                            // Gray out fainted Pokémon
                            if (isFainted) {
                                switchButtons[i].setAlpha(0.5f);
                                switchButtons[i].setTextColor(getResources().getColor(android.R.color.darker_gray));
                            } else {
                                switchButtons[i].setAlpha(1.0f);
                                switchButtons[i].setTextColor(getResources().getColor(android.R.color.black));
                            }
                            
                            // Show the button
                            switchButtons[i].setVisibility(View.VISIBLE);
                            
                            // Set up the click listener with the proper tag for identification
                            switchButtons[i].setTag(i);
                            final int pokeIndex = i;
                            final Button currentButton = switchButtons[i];
                            
                            // Set the click listener to use our main switchClick handler
                            currentButton.setOnClickListener(v -> {
                                int zeroBased = pokeIndex;
                                int oneBased = zeroBased + 1;
                                String pokemonName = currentButton.getText().toString();
                                
                                // Log this action
                                Log.d(TAG, "Switch selected: " + pokemonName + " (index: " + oneBased + ")");
                                
                                // Show waiting overlay BEFORE sending the command
                                showWaitingOverlay("You switched to: " + pokemonName);
                                
                                // Send the command to the server
                                socketClient.send("/choose switch " + oneBased);
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
        socketClient = new ShowdownWebSocketClient(this, message -> {
            runOnUiThread(() -> {
                battleLog.append(message + "\n");
                // Scroll to the bottom when new messages are added
                scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
                
                // Try to load Pokemon sprites when they're mentioned
                if (message.contains("switched in")) {
                    String pokemonName = extractPokemonName(message);
                    if (pokemonName != null) {
                        if (message.contains("You switched in")) {
                            loadPokemonSprite(pokemonName, true);
                        } else if (message.contains("Opponent switched in")) {
                            loadPokemonSprite(pokemonName, false);
                        }
                    }
                }
                
                // Hide waiting overlay when it's our turn again
                if (message.contains("What will") && message.contains("do?")) {
                    hideWaitingOverlay();
                }
                
                // Hide finding opponent overlay when battle is found
                if (message.contains("Battle started")) {
                    hideFindingOpponentOverlay();
                }
            });
        });
        
        // Set the battle data callback
        socketClient.setBattleDataCallback(this);
        
        // Show the finding opponent overlay when connecting
        showFindingOpponentOverlay();
        
        socketClient.connect();
    }
    
    /**
     * Shows the finding opponent overlay when searching for a battle
     */
    private void showFindingOpponentOverlay() {
        runOnUiThread(() -> {
            Log.d(TAG, "Showing finding opponent overlay");
            
            // Make sure all control panels are hidden
            viewControls.setVisibility(View.GONE);
            viewFightOpts.setVisibility(View.GONE);
            viewPartyOpts.setVisibility(View.GONE);
            
            // Ensure the controls container is still visible
            controlsContainer.setVisibility(View.VISIBLE);
            
            // Show the finding overlay
            findingOverlay.show();
            
            // Update Pokémon information to show "Waiting for Opponent"
            updatePokemonInfoForWaiting(true);
            
            // Log the action
            battleLog.append("Searching for an opponent...\n");
            scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
            
            // Force layout pass to ensure overlay is visible
            controlsContainer.requestLayout();
            controlsContainer.invalidate();
        });
    }
    
    /**
     * Hides the finding opponent overlay
     */
    private void hideFindingOpponentOverlay() {
        runOnUiThread(() -> {
            Log.d(TAG, "Hiding finding opponent overlay");
            
            if (findingOverlay != null && findingOverlay.isShowing()) {
                findingOverlay.hide();
                
                // Restore normal Pokémon information display
                updatePokemonInfoForWaiting(false);
                
                // Show the main controls after hiding the overlay
                showMainControls();
            }
        });
    }
    
    /**
     * Handles the cancel button click in the finding opponent overlay
     */
    private void cancelFindingOpponent() {
        // This method is no longer used since we removed the cancel button
        // Keeping it for now in case we need to add cancellation via another method
        if (socketClient != null) {
            socketClient.send("|/cancelchallenging");
            socketClient.send("|/cancelsearch");
        }
        
        hideFindingOpponentOverlay();
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
        


        // Try loading animated sprite first from gen 8 animations
        String animatedUrl = isPlayer
            ? "https://play.pokemonshowdown.com/sprites/ani-back/"  + ".gif"
            : "https://play.pokemonshowdown.com/sprites/ani/"  + ".gif";

        // Fallback to static sprite if animated fails
        String staticUrl = isPlayer
            ? "https://play.pokemonshowdown.com/sprites/gen5-back/"  + ".png"
            : "https://play.pokemonshowdown.com/sprites/gen5/"  + ".png";

        // Second fallback for newer Pokémon (Gen 9) that might only be in dex sprites
        String dexUrl = "https://play.pokemonshowdown.com/sprites/dex/" + ".png";

        // Third fallback for any missing Pokémon - use the official artwork
        String officialArtUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/"  + ".png";
        
        Log.d(TAG, "Attempting to load sprite for: " + pokemonName);
        Log.d(TAG, "Animated URL: " + animatedUrl);
        Log.d(TAG, "Static URL: " + staticUrl);
        Log.d(TAG, "Dex URL: " + dexUrl);
        Log.d(TAG, "Official Art URL: " + officialArtUrl);
        
        // Try to load animated sprite with fallback to static, then to dex, then to official art
        Glide.with(this)
            .load(animatedUrl)
            .error(Glide.with(this)
                .load(staticUrl)
                .error(Glide.with(this)
                    .load(dexUrl)
                    .error(Glide.with(this)
                        .load(officialArtUrl))))
            .listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                    Log.e(TAG, "Failed to load sprite for: " + pokemonName + " - " + e.getMessage());
                    return false; // Let Glide handle the fallback
                }

                @Override
                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                    Log.d(TAG, "Successfully loaded sprite for: " + pokemonName + " from " + dataSource.name());
                    return false; // Continue as normal
                }
            })
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(isPlayer ? playerSprite : opponentSprite);
    }
    
    /**
     * Helper method to get Pokémon ID by name for official artwork URL
     * This is a simple mapping for some common Pokémon, would need to be expanded
     */
    private int getPokemonIdByName(String formattedName) {
        // This would ideally be a complete mapping or API call
        // For now, just handle some common cases and return a default
        switch (formattedName) {
            case "bulbasaur": return 1;
            case "charmander": return 4;
            case "squirtle": return 7;
            case "pikachu": return 25;
            case "eevee": return 133;
            case "mewtwo": return 150;
            case "scream-tail": return 954;
            case "flutter-mane": return 953;
            case "great-tusk": return 984;
            case "brute-bonnet": return 986;
            case "sandy-shocks": return 989;
            case "iron-treads": return 990;
            case "iron-bundle": return 991;
            case "iron-hands": return 992;
            case "iron-jugulis": return 993;
            case "iron-moth": return 994;
            case "iron-thorns": return 995;
            case "iron-valiant": return 1000;
            case "walking-wake": return 1009;
            case "iron-leaves": return 1010;
            case "gouging-fire": return 1017;
            case "raging-bolt": return 1018;
            default: 
                // If we don't have a mapping, try to extract a number from the name
                // This works for many Pokémon with forms like "charizard-mega-x"
                try {
                    String baseForm = formattedName.split("-")[0];
                    // For Pokémon without a number in the name, return Missingno (0)
                    return 0;
                } catch (Exception e) {
                    return 0; // Default to Missingno
                }
        }
    }

    /**
     * Loads a random battle background from Pokémon Showdown
     */
    private void loadRandomBattleBackground() {
        try {
            // Select a random background from the array
            int randomIndex = (int) (Math.random() * BATTLE_BACKGROUNDS.length);
            String backgroundName = BATTLE_BACKGROUNDS[randomIndex];
            
            // Construct the URL for the background image
            String backgroundUrl = "https://play.pokemonshowdown.com/sprites/gen6bgs/" + backgroundName + ".jpg";
            
            Log.d(TAG, "Loading battle background: " + backgroundUrl);
            
            // Load the background image using Glide
            Glide.with(this)
                    .load(backgroundUrl)
                    .transition(DrawableTransitionOptions.withCrossFade(500))
                    .error(R.drawable.ic_launcher_background) // Fallback if loading fails
                    .into(battleBackground);
            
            // Start playing battle music
            SoundManager.getInstance(this).playBattleMusic();
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading battle background", e);
        }
    }

    /**
     * Enables immersive game mode to hide system UI elements
     * This provides a full-screen experience without navigation buttons
     */
    private void enableGameMode() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Re-apply immersive mode when window regains focus
            enableGameMode();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause background music when the activity is paused
        if (SoundManager.getInstance(this) != null) {
            SoundManager.getInstance(this).pauseBackgroundMusic();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set battle mode and update music state when the activity comes back to the foreground
        if (SoundManager.getInstance(this) != null) {
            Log.d("BattleActivity", "Setting battle mode to true in onResume");
            SoundManager.getInstance(this).setInBattleMode(true);
            SoundManager.getInstance(this).updateMusicState();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Set battle mode to false when destroying
        if (SoundManager.getInstance(this) != null) {
            // Set battle mode to false
            SoundManager.getInstance(this).setInBattleMode(false);
            
            // Stop background music
            SoundManager.getInstance(this).stopBackgroundMusic();
            
            // Release all sound resources
            SoundManager.getInstance(this).release();
        }
        
        // Close the WebSocket connection
        if (socketClient != null) {
            socketClient.close();
        }
        
        Log.d(TAG, "BattleActivity destroyed, all audio stopped");
    }

    /**
     * Navigate back to the main menu after a battle is complete
     * This method ensures we only navigate once even if called multiple times
     */
    public void navigateToMainMenu() {
        if (isNavigatingToMainMenu) {
            Log.d(TAG, "Already navigating to main menu, ignoring duplicate request");
            return;
        }
        
        isNavigatingToMainMenu = true;
        Log.d(TAG, "Navigating to main menu from BattleActivity");
        
        // Run on UI thread to ensure proper activity state
        runOnUiThread(() -> {
            try {
                // Create an intent to start the MainActivity
                android.content.Intent intent = new android.content.Intent(this, MainActivity.class);
                
                // Add flags to clear the back stack and start a new task
                intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                               android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                
                // Start the main activity
                startActivity(intent);
                
                // Finish this activity to remove it from the back stack
                finish();
                
                Log.d(TAG, "Successfully started MainActivity and finished BattleActivity");
            } catch (Exception e) {
                Log.e(TAG, "Error navigating to main menu", e);
                isNavigatingToMainMenu = false;
            }
        });
    }

    @Override
    public void onBattleStart() {
        runOnUiThread(() -> {
            // Show the main controls when the battle starts
            viewControls.setVisibility(View.VISIBLE);
            viewFightOpts.setVisibility(View.GONE);
            viewPartyOpts.setVisibility(View.GONE);
            
            // Hide the waiting overlay if it's visible
            if (waitingOverlay != null) {
                waitingOverlay.hide();
            }
        });
    }
    
    @Override
    public void onTurnChange(int turnNumber) {
        runOnUiThread(() -> {
            Log.d(TAG, "New turn started: " + turnNumber);
            
            // Hide the waiting overlay when a new turn starts
            if (waitingOverlay != null) {
                waitingOverlay.hide();
            }
            
            // Show the main controls
            viewControls.setVisibility(View.VISIBLE);
            viewFightOpts.setVisibility(View.GONE);
            viewPartyOpts.setVisibility(View.GONE);
        });
    }

    @Override
    public void onPokemonSwitch(String position, String pokemonName, String details, String hpStatus) {
        // Forward to the battle manager
        battleManager.onPokemonSwitch(position, pokemonName, details, hpStatus);
    }

    @Override
    public void onHPChange(String position, String hpStatus) {
        // Forward to the battle manager
        battleManager.onHPChange(position, hpStatus);
    }

    @Override
    public void onFaint(String position) {
        // Forward to the battle manager
        battleManager.onFaint(position);
        
        // Check if this is the player's Pokémon that fainted
        String playerNum = position.substring(1, 2);
        if (playerNum.equals(String.valueOf(socketClient.getPlayerSlot()))) {
            // Check if we need to force-switch
            JSONObject requestJson = socketClient.getLastRequestJson();
            if (requestJson != null) {
                try {
                    // Check if the request has forceSwitch field set to true
                    if (requestJson.has("forceSwitch") && !requestJson.isNull("forceSwitch")) {
                        JSONArray forceSwitch = requestJson.getJSONArray("forceSwitch");
                        if (forceSwitch.length() > 0 && forceSwitch.getBoolean(0)) {
                            // We need to force-switch, show the party options immediately
                            runOnUiThread(() -> {
                                Log.d(TAG, "Force-switch required after faint, showing party options");
                                
                                // Hide any waiting overlay if it's showing
                                if (waitingOverlay != null && waitingOverlay.isShowing()) {
                                    waitingOverlay.hide();
                                }
                                
                                // Show the party options and hide other controls
                                viewControls.setVisibility(View.GONE);
                                viewFightOpts.setVisibility(View.GONE);
                                viewPartyOpts.setVisibility(View.VISIBLE);
                                
                                // Refresh the switch buttons to show current state
                                refreshSwitchButtons();
                            });
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error checking for force-switch after faint", e);
                }
            }
        }
    }
    
    @Override
    public void onPlayerSlotSet(int slot) {
        // Forward to the battle manager
        battleManager.onPlayerSlotSet(slot);
    }

    /**
     * Called when a new request is received from the server
     * This is a good place to check for forced switches from moves like U-turn
     */
    public void onRequest(JSONObject requestJson) {
        // Check if we need to force-switch due to a move like U-turn
        if (requestJson != null) {
            try {
                // Check if the request has forceSwitch field set to true
                if (requestJson.has("forceSwitch") && !requestJson.isNull("forceSwitch")) {
                    JSONArray forceSwitch = requestJson.getJSONArray("forceSwitch");
                    if (forceSwitch.length() > 0 && forceSwitch.getBoolean(0)) {
                        // We need to force-switch, show the party options immediately
                        runOnUiThread(() -> {
                            Log.d(TAG, "Force-switch required after move (like U-turn), showing party options");
                            
                            // Hide any waiting overlay if it's showing
                            if (waitingOverlay != null && waitingOverlay.isShowing()) {
                                waitingOverlay.hide();
                            }
                            
                            // Show the party options and hide other controls
                            viewControls.setVisibility(View.GONE);
                            viewFightOpts.setVisibility(View.GONE);
                            viewPartyOpts.setVisibility(View.VISIBLE);
                            
                            // Refresh the switch buttons to show current state
                            refreshSwitchButtons();
                        });
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error checking for force-switch after move", e);
            }
        }
    }

    /**
     * Shows the waiting overlay with the specified action text
     */
    private void showWaitingOverlay(String actionText) {
        runOnUiThread(() -> {
            Log.d(TAG, "Showing waiting overlay: " + actionText);
            
            // Make sure all control panels are hidden
            viewControls.setVisibility(View.GONE);
            viewFightOpts.setVisibility(View.GONE);
            viewPartyOpts.setVisibility(View.GONE);
            
            // Ensure the controls container is still visible
            controlsContainer.setVisibility(View.VISIBLE);
            
            // Ensure the overlay is properly initialized
            if (waitingOverlay == null) {
                waitingOverlay = new BattleWaitingOverlay(this, controlsContainer, v -> cancelWaitingAction());
            }
            
            // Show the waiting overlay
            waitingOverlay.show(actionText);
            
            // Log the action
            battleLog.append(actionText + "\n");
            scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
            
            // Force layout pass to ensure overlay is visible
            controlsContainer.requestLayout();
            controlsContainer.invalidate();
        });
    }
    
    /**
     * Hides the waiting overlay
     */
    private void hideWaitingOverlay() {
        runOnUiThread(() -> {
            Log.d(TAG, "Hiding waiting overlay");
            
            if (waitingOverlay != null && waitingOverlay.isShowing()) {
                waitingOverlay.hide();
                
                // Show the main controls after hiding the overlay
                showMainControls();
            }
        });
    }
    
    /**
     * Handles the cancel button click in the waiting overlay
     */
    private void cancelWaitingAction() {
        // Send a cancel command to the server
        socketClient.send("/cancel");
        
        // Hide the overlay
        hideWaitingOverlay();
        
        // Show the main controls
        showMainControls();
        
        // Log the cancellation
        battleLog.append("You canceled your action.\n");
        scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
    }

    /**
     * Get the current battle log text
     * @return The battle log text
     */
    public String getBattleLog() {
        if (battleLog != null) {
            return battleLog.getText().toString();
        }
        return "";
    }
    
    /**
     * Get the WebSocket client
     * @return The ShowdownWebSocketClient instance
     */
    public ShowdownWebSocketClient getSocketClient() {
        return socketClient;
    }

    /**
     * Updates the Pokémon information display based on waiting state
     * @param isWaiting true if waiting for opponent, false otherwise
     */
    private void updatePokemonInfoForWaiting(boolean isWaiting) {
        if (isWaiting) {
            // Replace player and opponent Pokémon info with "Waiting for Opponent"
            playerPokemonInfo.setText("Waiting for Opponent");
            opponentPokemonInfo.setText("Waiting for Opponent");
            
            // Also refresh switch buttons to show waiting status
            refreshSwitchButtons();
            
            Log.d(TAG, "Updated Pokémon info to show 'Waiting for Opponent'");
        } else {
            // Restore normal display - get data from battle manager if available
            JSONObject requestJson = socketClient.getLastRequestJson();
            if (requestJson != null) {
                try {
                    // Update player Pokémon info
                    if (requestJson.has("side") && !requestJson.isNull("side")) {
                        JSONObject side = requestJson.getJSONObject("side");
                        if (side.has("pokemon") && !side.isNull("pokemon")) {
                            JSONArray pokemon = side.getJSONArray("pokemon");
                            if (pokemon.length() > 0) {
                                for (int i = 0; i < pokemon.length(); i++) {
                                    JSONObject poke = pokemon.getJSONObject(i);
                                    if (poke.has("active") && poke.getBoolean("active")) {
                                        String pokeName = poke.getString("ident").split(":")[1];
                                        int level = 100; // Default level
                                        
                                        // Extract level from details if available
                                        if (poke.has("details") && !poke.isNull("details")) {
                                            String details = poke.getString("details");
                                            if (details.contains(", L")) {
                                                try {
                                                    int levelIndex = details.indexOf(", L") + 3;
                                                    int endIndex = details.indexOf(",", levelIndex);
                                                    if (endIndex == -1) endIndex = details.length();
                                                    String levelStr = details.substring(levelIndex, endIndex);
                                                    level = Integer.parseInt(levelStr);
                                                } catch (Exception e) {
                                                    Log.e(TAG, "Error parsing level from details: " + details, e);
                                                }
                                            }
                                        }
                                        
                                        playerPokemonInfo.setText(pokeName + " Lv." + level);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    
                    // Update opponent Pokémon info if available
                    if (requestJson.has("opponent") && !requestJson.isNull("opponent")) {
                        JSONObject opponent = requestJson.getJSONObject("opponent");
                        if (opponent.has("activePokemon") && !opponent.isNull("activePokemon")) {
                            String opponentPokemon = opponent.getString("activePokemon");
                            opponentPokemonInfo.setText(opponentPokemon);
                        }
                    }
                    
                    // Also refresh switch buttons to show normal status
                    refreshSwitchButtons();
                    
                    Log.d(TAG, "Restored normal Pokémon info display");
                } catch (JSONException e) {
                    Log.e(TAG, "Error updating Pokémon info after waiting", e);
                }
            }
        }
    }

    /**
     * Sets up the forfeit button with confirmation dialog
     */
    private void setupForfeitButton() {
        // The forfeit button has been removed from the layout and moved to the menu
        // This method is kept for backward compatibility but doesn't do anything
    }
}
