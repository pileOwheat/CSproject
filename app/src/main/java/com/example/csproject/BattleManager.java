package com.example.csproject;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

/**
 * Manager class for handling battle state and UI updates
 */
public class BattleManager implements ShowdownWebSocketClient.BattleDataCallback {
    private static final String TAG = "BattleManager";
    
    // UI components
    private final TextView playerPokemonInfo;
    private final TextView opponentPokemonInfo;
    private final ProgressBar playerHP;
    private final ProgressBar opponentHP;
    private final ImageView playerSprite;
    private final ImageView opponentSprite;
    private final Activity activity;
    
    // Battle data
    private final Map<String, PokemonBattleData> activePokemon = new HashMap<>();
    private int playerSlot = 1; // Default to p1, will be updated based on the request JSON
    
    /**
     * Create a new BattleManager
     * @param activity The activity context for running UI updates
     * @param playerPokemonInfo TextView for player's Pokémon info
     * @param opponentPokemonInfo TextView for opponent's Pokémon info
     * @param playerHP ProgressBar for player's HP
     * @param opponentHP ProgressBar for opponent's HP
     * @param playerSprite ImageView for player's Pokémon sprite
     * @param opponentSprite ImageView for opponent's Pokémon sprite
     */
    public BattleManager(Activity activity, TextView playerPokemonInfo, TextView opponentPokemonInfo, 
                         ProgressBar playerHP, ProgressBar opponentHP,
                         ImageView playerSprite, ImageView opponentSprite) {
        this.activity = activity;
        this.playerPokemonInfo = playerPokemonInfo;
        this.opponentPokemonInfo = opponentPokemonInfo;
        this.playerHP = playerHP;
        this.opponentHP = opponentHP;
        this.playerSprite = playerSprite;
        this.opponentSprite = opponentSprite;
    }
    
    /**
     * Set the player's slot (p1 or p2)
     * @param slot The player's slot (1 or 2)
     */
    @Override
    public void onPlayerSlotSet(int slot) {
        Log.d(TAG, "Player slot set to: " + slot);
        this.playerSlot = slot;
        // Update UI immediately in case we already have Pokemon data
        updateUI();
    }
    
    /**
     * Set the player's slot (p1 or p2)
     * @param slot The player's slot (1 or 2)
     * @deprecated Use onPlayerSlotSet instead
     */
    @Deprecated
    public void setPlayerSlot(int slot) {
        onPlayerSlotSet(slot);
    }
    
    /**
     * Handle a Pokémon switch event
     * @param position The position identifier (e.g., "p1a", "p2a")
     * @param pokemonName The Pokémon's name
     * @param details The details string from the protocol
     * @param hpStatus The HP status string from the protocol
     */
    @Override
    public void onPokemonSwitch(String position, String pokemonName, String details, String hpStatus) {
        Log.d(TAG, "Switch: " + position + " " + pokemonName + " " + details + " " + hpStatus);
        
        // Create or update the Pokémon data
        PokemonBattleData pokemon = new PokemonBattleData(position, pokemonName, details, hpStatus);
        activePokemon.put(position, pokemon);
        
        // Update UI based on whether this is the player's or opponent's Pokémon
        updateUI();
    }
    
    /**
     * Handle an HP change event
     * @param position The position identifier (e.g., "p1a", "p2a")
     * @param hpStatus The HP status string from the protocol
     */
    @Override
    public void onHPChange(String position, String hpStatus) {
        Log.d(TAG, "HP Change: " + position + " " + hpStatus);
        
        PokemonBattleData pokemon = activePokemon.get(position);
        if (pokemon != null) {
            // Store old HP percentage for animation
            int oldHPPercentage = pokemon.getHPPercentage();
            
            // Update HP
            pokemon.updateHP(hpStatus);
            
            // Get new HP percentage
            int newHPPercentage = pokemon.getHPPercentage();
            
            // Check if this is the player's Pokémon and if health is now low
            boolean isPlayerPokemon = position.startsWith("p" + playerSlot);
            if (isPlayerPokemon && oldHPPercentage > 20 && newHPPercentage <= 20) {
                // Play low health sound when health becomes critical
                SoundManager soundManager = SoundManager.getInstance(activity);
                soundManager.playSoundEffect(SoundManager.SFX_NOT_EFFECTIVE);
            }
            
            updateUI(oldHPPercentage, position);
        } else {
            Log.w(TAG, "Tried to update HP for unknown Pokemon at position: " + position);
        }
    }
    
    /**
     * Handle a faint event
     * @param position The position identifier (e.g., "p1a", "p2a")
     */
    @Override
    public void onFaint(String position) {
        Log.d(TAG, "Pokémon fainted at position: " + position);
        
        // Update the battle data to reflect the fainted Pokémon
        if (activePokemon.containsKey(position)) {
            PokemonBattleData pokemon = activePokemon.get(position);
            if (pokemon != null) {
                // Mark the Pokémon as fainted
                pokemon.setFainted();
                
                // Play faint sound effect
                SoundManager soundManager = SoundManager.getInstance(activity);
                soundManager.playSoundEffect(SoundManager.SFX_DEFEAT);
                
                // Play the Pokémon's cry by its name
                String pokemonName = pokemon.getName();
                if (pokemonName != null && !pokemonName.isEmpty()) {
                    soundManager.playPokemonCryByName(pokemonName);
                    Log.d(TAG, "Playing fainted cry for: " + pokemonName);
                }
                
                // Update the UI to reflect the fainted Pokémon
                activity.runOnUiThread(this::updateUI);
            }
        }
    }
    
    /**
     * Handle a battle start event
     */
    @Override
    public void onBattleStart() {
        Log.d(TAG, "Battle started!");
        
        // Clear any existing battle data
        activePokemon.clear();
        
        // Update UI to show battle is starting
        activity.runOnUiThread(() -> {
            playerPokemonInfo.setText("Waiting for Pokémon...");
            opponentPokemonInfo.setText("Waiting for Pokémon...");
            playerHP.setProgress(100);
            opponentHP.setProgress(100);
            
            // Make sure the battle controls are visible
            View controlsContainer = activity.findViewById(R.id.controlsContainer);
            if (controlsContainer != null) {
                controlsContainer.setVisibility(View.VISIBLE);
            }

            // Find the main controls view directly instead of using getChildAt
            View viewControls = activity.findViewById(R.id.buttonFight).getParent() instanceof View ?
                               (View) activity.findViewById(R.id.buttonFight).getParent() : null;
            if (viewControls != null) {
                viewControls.setVisibility(View.VISIBLE);
            }

            // Force refresh the UI when battle data is available
            if (activity instanceof BattleActivity) {
                BattleActivity battleActivity = (BattleActivity) activity;
                battleActivity.refreshBattleControls();
            }
        });
    }
    
    /**
     * Handle a turn change event
     * @param turnNumber The current turn number
     */
    @Override
    public void onTurnChange(int turnNumber) {
        // Handle turn changes in the battle manager
        Log.d(TAG, "Turn changed to: " + turnNumber);
        
        // We can add any battle state updates needed for turn changes here
    }
    
    /**
     * Called when a new request is received from the server
     * @param requestJson The JSON object containing the request data
     */
    @Override
    public void onRequest(org.json.JSONObject requestJson) {
        // BattleManager doesn't need to handle requests directly
        // The BattleActivity handles the request processing
        Log.d(TAG, "Request received in BattleManager, forwarding to BattleActivity");
    }
    
    /**
     * Update the UI with the current battle state
     */
    private void updateUI() {
        updateUI(-1, null);
    }
    
    /**
     * Update the UI with the current battle state and animate health changes
     * @param oldHPPercentage The previous HP percentage for animation, or -1 if no animation
     * @param changedPosition The position that changed, or null if not applicable
     */
    private void updateUI(int oldHPPercentage, String changedPosition) {
        // Find player and opponent Pokémon
        final PokemonBattleData playerPokemon = findPokemonByPosition("p" + playerSlot + "a");
        final PokemonBattleData opponentPokemon = findPokemonByPosition("p" + (3 - playerSlot) + "a");
        
        Log.d(TAG, "Updating UI - Player slot: " + playerSlot);
        Log.d(TAG, "Active Pokemon: " + activePokemon.keySet());
        
        // Run UI updates on the main thread
        activity.runOnUiThread(() -> {
            // Update player Pokémon info
            if (playerPokemon != null) {
                Log.d(TAG, "Player Pokemon: " + playerPokemon.getName() + " HP: " + playerPokemon.getHPPercentage() + "%");
                playerPokemonInfo.setText(playerPokemon.getName() + " Lv." + playerPokemon.getLevel());
                
                // Determine if this Pokémon's HP changed
                boolean animatePlayerHP = changedPosition != null && 
                                         changedPosition.startsWith("p" + playerSlot) && 
                                         oldHPPercentage >= 0;
                
                // Update health bar color based on health percentage
                updateHealthBarColor(playerHP, playerPokemon.getHPPercentage());
                
                // Animate HP change if needed
                if (animatePlayerHP) {
                    animateHealthChange(playerHP, oldHPPercentage, playerPokemon.getHPPercentage());
                } else {
                    playerHP.setProgress(playerPokemon.getHPPercentage());
                }
                
                // Only load player sprite if the ImageView is empty or if this is a new Pokémon
                if (playerSprite.getTag() == null || !playerPokemon.getName().equals(playerSprite.getTag().toString())) {
                    loadPokemonSprite(playerPokemon.getName(), true);
                    playerSprite.setTag(playerPokemon.getName());
                }
            } else {
                Log.d(TAG, "No player Pokemon found at position p" + playerSlot + "a");
            }
            
            // Update opponent Pokémon info
            if (opponentPokemon != null) {
                Log.d(TAG, "Opponent Pokemon: " + opponentPokemon.getName() + " HP: " + opponentPokemon.getHPPercentage() + "%");
                opponentPokemonInfo.setText(opponentPokemon.getName() + " Lv." + opponentPokemon.getLevel());
                
                // Determine if this Pokémon's HP changed
                boolean animateOpponentHP = changedPosition != null && 
                                           changedPosition.startsWith("p" + (3 - playerSlot)) && 
                                           oldHPPercentage >= 0;
                
                // Update health bar color based on health percentage
                updateHealthBarColor(opponentHP, opponentPokemon.getHPPercentage());
                
                // Animate HP change if needed
                if (animateOpponentHP) {
                    animateHealthChange(opponentHP, oldHPPercentage, opponentPokemon.getHPPercentage());
                } else {
                    opponentHP.setProgress(opponentPokemon.getHPPercentage());
                }
                
                // Only load opponent sprite if the ImageView is empty or if this is a new Pokémon
                if (opponentSprite.getTag() == null || !opponentPokemon.getName().equals(opponentSprite.getTag().toString())) {
                    loadPokemonSprite(opponentPokemon.getName(), false);
                    opponentSprite.setTag(opponentPokemon.getName());
                }
            } else {
                Log.d(TAG, "No opponent Pokemon found at position p" + (3 - playerSlot) + "a");
            }
        });
    }
    
    /**
     * Find a Pokémon by its position
     * @param positionPrefix The position prefix to search for
     * @return The Pokémon at the specified position, or null if not found
     */
    private PokemonBattleData findPokemonByPosition(String positionPrefix) {
        // Direct lookup first
        PokemonBattleData pokemon = activePokemon.get(positionPrefix);
        if (pokemon != null) {
            return pokemon;
        }
        
        // If direct lookup fails, try to match by prefix
        for (Map.Entry<String, PokemonBattleData> entry : activePokemon.entrySet()) {
            String key = entry.getKey();
            // Check if the key starts with the position prefix (p1a or p2a)
            // or if it's a variant with extra characters (p1aa or p2aa)
            if (key.startsWith(positionPrefix) || 
                (positionPrefix.length() == 3 && key.startsWith(positionPrefix.substring(0, 2)) && key.contains(positionPrefix.substring(2)))) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * Load a Pokémon sprite into the appropriate ImageView
     * @param pokemonName The name of the Pokémon
     * @param isPlayer Whether this is the player's Pokémon or not
     */
    public void loadPokemonSprite(String pokemonName, boolean isPlayer) {
        if (pokemonName == null || pokemonName.isEmpty()) {
            return;
        }
        
        Context context = activity.getApplicationContext();
        ImageView targetView = isPlayer ? playerSprite : opponentSprite;
        
        // Format the Pokémon name for the URL
        String formattedName = pokemonName.toLowerCase()
                .replace(" ", "")
                .replace("-", "")
                .replace(".", "")
                .replace("'", "")
                .replace(":", "");
        
        // Special handling for paradox Pokémon
        if (pokemonName.contains("Walking Wake") || pokemonName.contains("WalkingWake")) {
            formattedName = "walkingwake";
        } else if (pokemonName.contains("Iron Leaves") || pokemonName.contains("IronLeaves")) {
            formattedName = "ironleaves";
        } else if (pokemonName.contains("Iron Moth") || pokemonName.contains("IronMoth")) {
            formattedName = "ironmoth";
        } else if (pokemonName.contains("Iron Hands") || pokemonName.contains("IronHands")) {
            formattedName = "ironhands";
        } else if (pokemonName.contains("Iron Jugulis") || pokemonName.contains("IronJugulis")) {
            formattedName = "ironjugulis";
        } else if (pokemonName.contains("Iron Thorns") || pokemonName.contains("IronThorns")) {
            formattedName = "ironthorns";
        } else if (pokemonName.contains("Iron Bundle") || pokemonName.contains("IronBundle")) {
            formattedName = "ironbundle";
        } else if (pokemonName.contains("Iron Valiant") || pokemonName.contains("IronValiant")) {
            formattedName = "ironvaliant";
        } else if (pokemonName.contains("Roaring Moon") || pokemonName.contains("RoaringMoon")) {
            formattedName = "roaringmoon";
        } else if (pokemonName.contains("Great Tusk") || pokemonName.contains("GreatTusk")) {
            formattedName = "greattusk";
        } else if (pokemonName.contains("Scream Tail") || pokemonName.contains("ScreamTail")) {
            formattedName = "screamtail";
        } else if (pokemonName.contains("Brute Bonnet") || pokemonName.contains("BruteBonnet")) {
            formattedName = "brutebonnet";
        } else if (pokemonName.contains("Flutter Mane") || pokemonName.contains("FlutterMane")) {
            formattedName = "fluttermane";
        } else if (pokemonName.contains("Slither Wing") || pokemonName.contains("SlitherWing")) {
            formattedName = "slitherwing";
        } else if (pokemonName.contains("Sandy Shocks") || pokemonName.contains("SandyShocks")) {
            formattedName = "sandyshocks";
        }
        
        Log.d(TAG, "Loading sprite for " + (isPlayer ? "player" : "opponent") + ": " + pokemonName + " (formatted: " + formattedName + ")");
        
        // Construct the sprite URL - player sprites are back view, opponent sprites are front view
        String spriteUrl = isPlayer ? 
                "https://play.pokemonshowdown.com/sprites/ani-back/" + formattedName + ".gif" : 
                "https://play.pokemonshowdown.com/sprites/ani/" + formattedName + ".gif";
        
        String finalSpriteUrl = spriteUrl;
        String finalFormattedName = formattedName;
        
        // Use a handler for posting to the main thread
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        
        Glide.with(context)
            .load(spriteUrl)
            .listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                    Log.e("BattleManager", "Failed to load sprite from " + finalSpriteUrl + ": " + (e != null ? e.getMessage() : "unknown error"));
                    
                    // Try a backup URL with a different format - use Handler to post to main thread
                    mainHandler.post(() -> {
                        // Try gen5 sprites first (they have better support for newer Pokémon)
                        String backupUrl = isPlayer ?
                                "https://play.pokemonshowdown.com/sprites/gen5-back/" + finalFormattedName + ".png" :
                                "https://play.pokemonshowdown.com/sprites/gen5/" + finalFormattedName + ".png";
                        
                        Glide.with(context)
                            .load(backupUrl)
                            .listener(new RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                    Log.e("BattleManager", "Failed to load gen5 sprite, trying dex sprites: " + backupUrl);
                                    
                                    // Try dex sprites as a last resort
                                    String dexUrl = "https://play.pokemonshowdown.com/sprites/dex/" + finalFormattedName + ".png";
                                    
                                    Glide.with(context)
                                        .load(dexUrl)
                                        .error(R.drawable.ic_launcher_foreground) // Final fallback
                                        .into(targetView);
                                    
                                    return true;
                                }

                                @Override
                                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                    return false; // Let Glide handle the resource
                                }
                            })
                            .into(targetView);
                    });
                    
                    return true;
                }

                @Override
                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                    return false; // Let Glide handle the resource
                }
            })
            .error(R.drawable.ic_launcher_foreground) // Fallback if loading fails
            .into(targetView);
    }
    
    /**
     * Update the health bar color based on the health percentage
     * @param healthBar The health bar to update
     * @param healthPercentage The current health percentage
     */
    private void updateHealthBarColor(ProgressBar healthBar, int healthPercentage) {
        if (healthPercentage > 50) {
            // Green for good health (> 50%)
            healthBar.setProgressDrawable(activity.getResources().getDrawable(R.drawable.health_bar_drawable));
        } else if (healthPercentage > 20) {
            // Yellow for moderate health (20-50%)
            healthBar.setProgressDrawable(activity.getResources().getDrawable(R.drawable.health_bar_yellow));
        } else {
            // Red for low health (< 20%)
            healthBar.setProgressDrawable(activity.getResources().getDrawable(R.drawable.health_bar_red));
        }
    }
    
    /**
     * Animate a health change from old value to new value
     * @param healthBar The health bar to animate
     * @param oldValue The old health percentage
     * @param newValue The new health percentage
     */
    private void animateHealthChange(ProgressBar healthBar, int oldValue, int newValue) {
        // Create a value animator to smoothly transition between old and new values
        ValueAnimator animator = ValueAnimator.ofInt(oldValue, newValue);
        
        // Calculate animation duration based on the amount of health change
        // Larger health drops take longer for more dramatic effect
        // Minimum 800ms, maximum 2000ms for very large health changes
        int healthChange = Math.abs(oldValue - newValue);
        int baseDuration = 800;
        int variableDuration = healthChange * 20;
        int animationDuration = Math.min(2000, baseDuration + variableDuration);
        
        animator.setDuration(animationDuration);
        
        // Use a custom interpolator for more natural health bar movement
        // For health loss (oldValue > newValue), use DecelerateInterpolator for a "slowing down" effect
        // For health gain (oldValue < newValue), use AccelerateDecelerateInterpolator for a smoother curve
        if (oldValue > newValue) {
            // Health is decreasing - decelerate for dramatic effect
            animator.setInterpolator(new DecelerateInterpolator(1.5f));
        } else {
            // Health is increasing - smooth acceleration and deceleration
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
        }
        
        // Update the progress bar as the animation runs
        animator.addUpdateListener(animation -> {
            int animatedValue = (int) animation.getAnimatedValue();
            healthBar.setProgress(animatedValue);
            
            // Update color during animation if needed
            updateHealthBarColor(healthBar, animatedValue);
        });
        
        // Use vsync for smoother animation rendering
        animator.setInterpolator(animator.getInterpolator());
        
        // Request high frame rate for smoother animation with Choreographer
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (animator.isRunning()) {
                    // Force the animator to update on the vsync signal
                    animator.setCurrentPlayTime(animator.getCurrentPlayTime());
                    Choreographer.getInstance().postFrameCallback(this);
                }
            }
        });
        
        // Start the animation
        animator.start();
    }
}
