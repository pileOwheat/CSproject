package com.example.csproject;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import okhttp3.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShowdownWebSocketClient extends WebSocketListener {
    public interface MessageCallback {
        void onMessageReceived(String msg);
    }

    public interface BattleDataCallback {
        void onPokemonSwitch(String position, String pokemonName, String details, String hpStatus);
        void onHPChange(String position, String hpStatus);
        void onFaint(String position);
        void onPlayerSlotSet(int slot);
        void onBattleStart(); // New method to handle battle start events
        void onTurnChange(int turnNumber); // New method to handle turn changes
        void onRequest(JSONObject requestJson); // New method to handle requests
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client;
    private final MessageCallback callback;
    private BattleDataCallback battleDataCallback;
    private WebSocket webSocket;
    private Context context; // Added context field

    private String battleRoomId;
    private JSONObject lastRequestJson;
    private int mySlot = -1;

    private static final int RETRY_INTERVAL_MS = 10000;
    private final Runnable retryRunnable = this::retrySearch;

    // Add class variables to track opponent Pokémon
    private String opponentPokemonName = null;
    private String opponentPokemonDetails = null;
    private String opponentPokemonCondition = null;

    // Add these fields to the class
    private boolean waitingForOpponent = false;
    private boolean waitingMessageSent = false;

    public ShowdownWebSocketClient(Context context, MessageCallback callback) {
        this.context = context; // Store the context
        this.callback = callback;
        this.client = new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build();
    }

    public void setBattleDataCallback(BattleDataCallback battleDataCallback) {
        this.battleDataCallback = battleDataCallback;
    }

    public void connect() {
        Request req = new Request.Builder().url("wss://sim3.psim.us/showdown/websocket").build();
        client.newWebSocket(req, this);
    }

    public void close() {
        handler.removeCallbacksAndMessages(null);
        if (webSocket != null) webSocket.close(1000, "User closed");
    }

    public void send(String msg) {
        if (webSocket == null) return;
        if (msg.startsWith("/")) {
            if (battleRoomId != null) webSocket.send(battleRoomId + "|" + msg);
            else callback.onMessageReceived("⚠️ Not in a battle yet.");
        } else {
            webSocket.send(msg);
        }
    }

    public JSONObject getLastRequestJson() {
        return lastRequestJson;
    }

    /**
     * Get the player's slot in the battle (1 or 2)
     * @return The player's slot, or -1 if not set yet
     */
    public int getPlayerSlot() {
        return mySlot;
    }
    
    /**
     * Check if we're currently waiting for the opponent
     * @return true if waiting for opponent, false otherwise
     */
    public boolean isWaitingForOpponent() {
        return waitingForOpponent;
    }

    @Override
    public void onOpen(WebSocket ws, Response resp) {
        this.webSocket = ws;
        callback.onMessageReceived("✅ Connected to Showdown server");
        
        // Set waiting for opponent to true when starting search
        waitingForOpponent = true;
        
        String guest = "guest" + (int) (Math.random() * 10000);
        ws.send("|/trn " + guest + ",0");

        ws.send("|/search randombattle");
        callback.onMessageReceived("🔍 Searching for a random battle...");
        
        // Schedule retry in case the search takes too long
        scheduleRetry();
    }

    @Override
    public void onMessage(WebSocket ws, String text) {
        handleBattleMessage(text);
    }

    private void handleBattleMessage(String message) {
        String[] lines = message.split("\n");
        String currentRoom = null;
        
        for (String line : lines) {
            if (line.startsWith(">")) {
                currentRoom = line.substring(1).trim();
                continue;
            } else if (line.startsWith("|")) {
                if (currentRoom != null && currentRoom.startsWith("battle-")) {
                    // We're in a battle room, process battle messages
                    if (battleRoomId == null) {
                        // First time seeing this battle room
                        battleRoomId = currentRoom;
                        stopRetryTimer(); // Stop retrying, we're in a battle
                        
                        // Log the battle room ID when we first discover it
                        callback.onMessageReceived("🎮 Battle started in lobby: " + battleRoomId);
                    }
                    processBattleMessage(line);
                } else {
                    // We're in the lobby or another room
                    handleLobbyUpdate(line.substring(1));
                }
            }
        }
    }

    private void handleLobbyUpdate(String update) {
        // Check if we're still searching for a battle
        if (update.contains("\"searching\":false") && update.contains("\"games\":{")) {
            // We found a battle, stop retrying
            stopRetryTimer();
            
            // No longer in the finding opponent state, but we're now waiting for the battle to start
            waitingForOpponent = true;
            
            callback.onMessageReceived("🎮 Found a battle! Waiting for it to start...");
        } else if (update.contains("\"searching\":true")) {
            // Still searching
            waitingForOpponent = true;
            callback.onMessageReceived("🔍 Searching for a battle...");
        }
    }

    /**
     * Process a battle message
     * @param message The message to process
     */
    private void processBattleMessage(String message) {
        Log.d("ShowdownClient", "Processing battle message: " + message);
        
        try {
            // Check if this is a battle update message
            if (message.startsWith("|")) {
                String[] parts = message.split("\\|");
                
                if (parts.length >= 2) {
                    String command = parts[1].trim();
                    
                    // Handle various battle commands
                    switch (command) {
                        case "init":
                            if (parts.length >= 3 && "battle".equals(parts[2])) {
                                // Battle has started
                                if (battleDataCallback != null) {
                                    battleDataCallback.onBattleStart();
                                }
                            }
                            break;
                        case "title":
                            if (parts.length >= 3) {
                                String battleTitle = parts[2];
                                callback.onMessageReceived("🏆 " + battleTitle);
                            }
                            break;
                        case "tier":
                            if (parts.length >= 3) {
                                String tier = parts[2];
                                callback.onMessageReceived("🎮 [" + tier + "]");
                            }
                            break;
                        case "rated":
                            callback.onMessageReceived("⭐ Rated battle");
                            break;
                        case "rule":
                            if (parts.length >= 3) {
                                String rule = parts[2];
                                callback.onMessageReceived("📜 " + rule);
                            }
                            break;
                        case "start":
                            callback.onMessageReceived("🚀 Battle started between " + 
                                (mySlot == 1 ? "you and your opponent!" : "your opponent and you!"));
                            break;
                        case "turn":
                            if (parts.length >= 3) {
                                String turnNumber = parts[2];
                                // Reset waiting status at the start of a new turn
                                waitingForOpponent = false;
                                waitingMessageSent = false;
                                callback.onMessageReceived("⏱️ Turn " + turnNumber);
                                if (battleDataCallback != null) {
                                    battleDataCallback.onTurnChange(Integer.parseInt(turnNumber));
                                }
                            }
                            break;
                        case "inactive":
                            if (parts.length >= 3) {
                                String inactiveMsg = parts[2];
                                callback.onMessageReceived("⏰ Battle timer is ON: " + inactiveMsg);
                            }
                            break;
                        case "player":
                            if (parts.length >= 4) {
                                String playerSlot = parts[2];
                                String playerName = parts[3];
                                Log.d("ShowdownClient", "Player: " + playerSlot + " = " + playerName);
                                
                                // Check if this is the current user's name
                                // We need to identify if this is the player by checking if the name contains "Guest"
                                // which is how we're connecting in the onOpen method
                                if (playerName.contains("Guest")) {
                                    // This is our player
                                    mySlot = Integer.parseInt(playerSlot.substring(1));
                                    if (battleDataCallback != null) {
                                        battleDataCallback.onPlayerSlotSet(mySlot);
                                    }
                                    Log.d("ShowdownClient", "Set my slot to: " + mySlot);
                                }
                            }
                            break;
                        case "switch":
                        case "drag":
                            // Format: |switch|POKEMON_IDENT|DETAILS|HP_STATUS
                            if (parts.length >= 5) {
                                String pokemonIdent = parts[2];
                                String details = parts[3];
                                String condition = parts[4];
                                
                                // Extract position and name
                                String[] identParts = pokemonIdent.split(":");
                                if (identParts.length >= 2) {
                                    String position = identParts[0].trim();
                                    String pokemonName = identParts[1].trim();
                                    
                                    Log.d("ShowdownClient", "Switch: " + position + " " + pokemonName + " " + details + " " + condition);
                                    
                                    // Play the Pokémon's cry when it's switched in
                                    SoundManager.getInstance(context).playPokemonCryByName(pokemonName);
                                    
                                    // Update battle manager with the switched Pokémon
                                    if (battleDataCallback != null) {
                                        battleDataCallback.onPokemonSwitch(position + "a", pokemonName, details, condition);
                                        
                                        // Create a message for the battle log
                                        if (callback != null) {
                                            // Extract the player number from the position (p1, p2, etc.)
                                            String playerNum = position.substring(1, 2);
                                            
                                            // Check if this is the player's Pokémon or the opponent's
                                            if (playerNum.equals(String.valueOf(mySlot))) {
                                                if (command.equals("switch")) {
                                                    callback.onMessageReceived("🔄 Go! " + pokemonName + "!");
                                                } else { // drag
                                                    callback.onMessageReceived("🔄 " + pokemonName + " was dragged out!");
                                                }
                                            } else {
                                                int opponentSlot = 3 - mySlot;
                                                if (playerNum.equals(String.valueOf(opponentSlot))) {
                                                    // This is the opponent's Pokémon
                                                    if (command.equals("switch")) {
                                                        callback.onMessageReceived("🔄 The opposing " + pokemonName + " was sent out!");
                                                    } else { // drag
                                                        callback.onMessageReceived("🔄 The opposing " + pokemonName + " was dragged out!");
                                                    }
                                                    
                                                    // Update our opponent data
                                                    opponentPokemonName = pokemonName;
                                                    opponentPokemonDetails = details;
                                                    opponentPokemonCondition = condition;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            break;
                        case "move":
                            // Format: |move|POKEMON_IDENT|MOVE_NAME|TARGET
                            if (parts.length >= 4) {
                                String pokemonIdent = parts[2];
                                String moveName = parts[3];
                                
                                // Extract position and name
                                String[] identParts = pokemonIdent.split(":");
                                if (identParts.length >= 2) {
                                    String position = identParts[0].trim();
                                    String pokemonName = identParts[1].trim();
                                    
                                    Log.d("ShowdownClient", "Move: " + position + " " + pokemonName + " used " + moveName);
                                    
                                    // Create a message for the battle log
                                    if (callback != null) {
                                        // Check if this is the player's Pokémon or the opponent's
                                        String playerNum = position.substring(1, 2);
                                        if (playerNum.equals(String.valueOf(mySlot))) {
                                            callback.onMessageReceived("⚡ " + pokemonName + " used " + moveName + "!");
                                        } else {
                                            callback.onMessageReceived("⚡ The opposing " + pokemonName + " used " + moveName + "!");
                                        }
                                    }
                                    
                                    // If this is the first time we're seeing the opponent's Pokémon in action,
                                    // update the battle manager
                                    int opponentSlot = 3 - mySlot;
                                    if (position.equals("p" + opponentSlot) && battleDataCallback != null) {
                                        // Check if we have a placeholder for the opponent
                                        if (opponentPokemonName == null || opponentPokemonName.contains("Unknown")) {
                                            // Update with the real Pokémon name
                                            opponentPokemonName = pokemonName;
                                            
                                            // Use default values if we don't have details
                                            if (opponentPokemonDetails == null) {
                                                opponentPokemonDetails = pokemonName + ", L50";
                                            }
                                            
                                            if (opponentPokemonCondition == null) {
                                                opponentPokemonCondition = "100/100";
                                            }
                                            
                                            // Notify the battle manager
                                            battleDataCallback.onPokemonSwitch("p" + opponentSlot + "a", 
                                                                              pokemonName, 
                                                                              opponentPokemonDetails, 
                                                                              opponentPokemonCondition);
                                        }
                                    }
                                }
                            }
                            break;
                        case "-terastallize":
                            // Format: |-terastallize|POKEMON_IDENT|TYPE
                            if (parts.length >= 4) {
                                String pokemonIdent = parts[2];
                                String teraType = parts[3];

                                // Extract position and name
                                String[] identParts = pokemonIdent.split(":");
                                if (identParts.length >= 2) {
                                    String position = identParts[0].trim();
                                    String pokemonName = identParts[1].trim();

                                    Log.d("ShowdownClient", "Terastallize: " + position + " " + pokemonName + " to " + teraType + " type");

                                    // Check if this is the player's Pokémon or the opponent's
                                    String playerNum = position.substring(1, 2);
                                    boolean isPlayer = playerNum.equals(String.valueOf(mySlot));
                                    String pokemonDisplay = isPlayer ? pokemonName : "The opposing " + pokemonName;

                                    // Create a message for the battle log
                                    if (callback != null) {
                                        callback.onMessageReceived("✨ " + pokemonDisplay + " terastallized into " + teraType + " type!");
                                    }
                                }
                            }
                            break;
                        case "damage":
                        case "-damage":
                        case "heal":
                        case "-heal":
                            // Format: |damage|POKEMON_IDENT|HP_STATUS
                            // or: |-damage|POKEMON_IDENT|HP_STATUS
                            if (parts.length >= 4) {
                                String pokemonIdent = parts[2];
                                String hpStatus = parts[3];
                                
                                // Extract position and name
                                String[] identParts = pokemonIdent.split(":");
                                if (identParts.length >= 2) {
                                    String position = identParts[0].trim();
                                    String pokemonName = identParts[1].trim();
                                    
                                    Log.d("ShowdownClient", "HP Change: " + position + " " + hpStatus);
                                    
                                    // Update battle manager with the HP change
                                    if (battleDataCallback != null) {
                                        battleDataCallback.onHPChange(position + "a", hpStatus);
                                    }
                                    
                                    // Calculate damage percentage if possible
                                    String damageMsg = "";
                                    try {
                                        if (hpStatus.contains("/")) {
                                            String[] hpParts = hpStatus.split("/");
                                            int currentHP = Integer.parseInt(hpParts[0]);
                                            int maxHP = Integer.parseInt(hpParts[1]);
                                            int percentage = (int) (((double) currentHP / maxHP) * 100);
                                            damageMsg = " (" + (100 - percentage) + "% damage)";
                                        }
                                    } catch (Exception e) {
                                        Log.e("ShowdownClient", "Error calculating damage percentage", e);
                                    }
                                    
                                    // Create a message for the battle log
                                    if (callback != null) {
                                        // Check if this is the player's Pokémon or the opponent's
                                        String playerNum = position.substring(1, 2);
                                        boolean isPlayer = playerNum.equals(String.valueOf(mySlot));
                                        String pokemonDisplay = isPlayer ? pokemonName : "The opposing " + pokemonName;
                                        
                                        if (command.equals("damage") || command.equals("-damage")) {
                                            if (parts.length >= 5 && parts[4].contains("poison")) {
                                                callback.onMessageReceived("☠️ " + pokemonDisplay + " was hurt by poison!" + damageMsg);
                                            } else if (parts.length >= 5 && parts[4].contains("burn")) {
                                                callback.onMessageReceived("🔥 " + pokemonDisplay + " was hurt by its burn!" + damageMsg);
                                            } else if (parts.length >= 5 && parts[4].contains("confusion")) {
                                                callback.onMessageReceived("😵 " + pokemonDisplay + " hurt itself in confusion!" + damageMsg);
                                            } else if (parts.length >= 5 && parts[4].contains("recoil")) {
                                                callback.onMessageReceived("💥 " + pokemonDisplay + " was damaged by the recoil!" + damageMsg);
                                            } else {
                                                callback.onMessageReceived("💢 " + pokemonDisplay + " took damage!" + damageMsg);
                                            }
                                        } else if (command.equals("heal") || command.equals("-heal")) {
                                            if (parts.length >= 5 && parts[4].contains("leftovers")) {
                                                callback.onMessageReceived("💊 " + pokemonDisplay + " restored a little HP using its Leftovers!");
                                            } else {
                                                callback.onMessageReceived("💚 " + pokemonDisplay + " restored its health!");
                                            }
                                        }
                                    }
                                }
                            }
                            break;
                        case "faint":
                            // Format: |faint|POKEMON_IDENT
                            if (parts.length >= 3) {
                                String pokemonIdent = parts[2];
                                
                                // Extract position and name
                                String[] identParts = pokemonIdent.split(":");
                                if (identParts.length >= 2) {
                                    String position = identParts[0].trim();
                                    String pokemonName = identParts[1].trim();
                                    
                                    Log.d("ShowdownClient", "Faint: " + position + " " + pokemonName);
                                    
                                    // Update battle manager with the faint
                                    if (battleDataCallback != null) {
                                        battleDataCallback.onFaint(position + "a");
                                    }
                                    
                                    // Create a message for the battle log
                                    if (callback != null) {
                                        // Check if this is the player's Pokémon or the opponent's
                                        String playerNum = position.substring(1, 2);
                                        boolean isPlayer = playerNum.equals(String.valueOf(mySlot));
                                        String pokemonDisplay = isPlayer ? pokemonName : "The opposing " + pokemonName;
                                        
                                        callback.onMessageReceived("💀 " + pokemonDisplay + " fainted!");
                                    }
                                }
                            }
                            break;
                        case "request":
                            // Format: |request|JSON_DATA
                            if (parts.length >= 3) {
                                try {
                                    lastRequestJson = new JSONObject(parts[2]);
                                    
                                    // Check if we're waiting for the opponent's move
                                    if (lastRequestJson.has("wait") && lastRequestJson.getBoolean("wait")) {
                                        waitingForOpponent = true;
                                    } else {
                                        waitingForOpponent = false;
                                    }
                                    
                                    // Only send the waiting message once
                                    if (waitingForOpponent && !waitingMessageSent) {
                                        callback.onMessageReceived("⌛ Waiting for opponent...");
                                        waitingMessageSent = true;
                                    } else if (!waitingForOpponent) {
                                        waitingMessageSent = false;
                                    }
                                    
                                    // Log that we received a new request
                                    Log.d("ShowdownClient", "New request received, player can now make a move");
                                    
                                    // Notify the battle activity about the new request
                                    if (battleDataCallback != null) {
                                        battleDataCallback.onRequest(lastRequestJson);
                                    }
                                } catch (JSONException e) {
                                    Log.e("ShowdownClient", "Error parsing request JSON", e);
                                }
                            }
                            break;
                        case "-crit":
                            // Format: |-crit|POKEMON_IDENT
                            if (parts.length >= 3) {
                                callback.onMessageReceived("⚠️ A critical hit!");
                            }
                            break;
                        case "-supereffective":
                            // Format: |-supereffective|POKEMON_IDENT
                            if (parts.length >= 3) {
                                callback.onMessageReceived("✨ It's super effective!");
                            }
                            break;
                        case "-resisted":
                            // Format: |-resisted|POKEMON_IDENT
                            if (parts.length >= 3) {
                                callback.onMessageReceived("🛡️ It's not very effective...");
                            }
                            break;
                        case "-immune":
                            // Format: |-immune|POKEMON_IDENT
                            if (parts.length >= 3) {
                                String pokemonIdent = parts[2];
                                
                                // Extract position and name
                                String[] identParts = pokemonIdent.split(":");
                                if (identParts.length >= 2) {
                                    String pokemonName = identParts[1].trim();
                                    callback.onMessageReceived("🛑 It doesn't affect " + pokemonName + "...");
                                } else {
                                    callback.onMessageReceived("🛑 It doesn't affect the target...");
                                }
                            }
                            break;
                        case "-miss":
                            // Format: |-miss|SOURCE_IDENT|TARGET_IDENT
                            if (parts.length >= 3) {
                                callback.onMessageReceived("❌ The attack missed!");
                            }
                            break;
                        case "-fail":
                            // Format: |-fail|POKEMON_IDENT|MOVE|REASON
                            if (parts.length >= 3) {
                                String pokemonIdent = parts[2];
                                
                                // Extract position and name
                                String[] identParts = pokemonIdent.split(":");
                                if (identParts.length >= 2) {
                                    String pokemonName = identParts[1].trim();
                                    
                                    if (parts.length >= 4 && parts[3].equals("protect")) {
                                        callback.onMessageReceived("But it failed!");
                                    } else if (parts.length >= 4 && parts[3].equals("Protect")) {
                                        callback.onMessageReceived(pokemonName + " protected itself!");
                                    } else {
                                        callback.onMessageReceived("But it failed!");
                                    }
                                } else {
                                    callback.onMessageReceived("But it failed!");
                                }
                            }
                            break;
                        case "win":
                            // Format: |win|PLAYER_NAME
                            if (parts.length >= 3) {
                                String winner = parts[2];
                                
                                // Check if the player won or lost
                                String outcome;
                                String opponentName;
                                
                                if (winner.contains("Guest")) {
                                    // Player won
                                    outcome = "win";
                                    
                                    // When player wins, we need to get the opponent name
                                    // Try to get it from the battleRoomId
                                    opponentName = "Opponent"; // Default fallback
                                    
                                    if (battleRoomId != null && battleRoomId.contains("-")) {
                                        // Battle room format is typically: battle-gen9randombattle-username1-username2
                                        String[] roomParts = battleRoomId.split("-");
                                        
                                        // Skip "battle" and "gen9randombattle" parts
                                        for (int i = 2; i < roomParts.length; i++) {
                                            if (!roomParts[i].equalsIgnoreCase("battle") && 
                                                !roomParts[i].contains("Guest") && 
                                                !roomParts[i].contains("randombattle") &&
                                                !roomParts[i].isEmpty()) {
                                                opponentName = roomParts[i];
                                                break;
                                            }
                                        }
                                    }
                                    
                                    callback.onMessageReceived("🏆 You won the battle against " + opponentName + "!");
                                } else {
                                    // Player lost
                                    outcome = "loss";
                                    opponentName = winner; // Winner is the opponent
                                    callback.onMessageReceived("😔 You lost the battle against " + opponentName + "!");
                                }
                                
                                // Save battle history to Firebase if user is signed in
                                saveBattleHistory(opponentName, outcome);
                            }
                            break;
                        case "tie":
                            // Format: |tie
                            callback.onMessageReceived("🤝 The battle ended in a tie!");
                            
                            // Save tie battle to Firebase
                            saveBattleHistory("Tie", "tie");
                            break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ShowdownClient", "Error processing battle message", e);
        }
    }

    /**
     * Save battle history to Firebase
     * @param opponent Name of the opponent
     * @param outcome Result of the battle (win, loss, or tie)
     */
    private void saveBattleHistory(String opponent, String outcome) {
        // Get battle log from the callback if it's a BattleActivity
        String battleLog = "";
        String playerTeam = "";
        String opponentTeam = "";
        
        if (context instanceof BattleActivity) {
            BattleActivity battleActivity = (BattleActivity) context;
            battleLog = battleActivity.getBattleLog();
            
            // Get team information if available
            if (lastRequestJson != null) {
                try {
                    // Extract player's team
                    if (lastRequestJson.has("side") && lastRequestJson.getJSONObject("side").has("pokemon")) {
                        JSONArray pokemon = lastRequestJson.getJSONObject("side").getJSONArray("pokemon");
                        StringBuilder teamBuilder = new StringBuilder();
                        for (int i = 0; i < pokemon.length(); i++) {
                            JSONObject poke = pokemon.getJSONObject(i);
                            if (poke.has("details")) {
                                teamBuilder.append(poke.getString("details"));
                                if (i < pokemon.length() - 1) {
                                    teamBuilder.append(", ");
                                }
                            }
                        }
                        playerTeam = teamBuilder.toString();
                    }
                    
                    // For opponent's team, we might not have complete information
                    // We'll use what we know from the battle
                    opponentTeam = "Unknown opponent team";
                } catch (JSONException e) {
                    Log.e("ShowdownClient", "Error extracting team information", e);
                }
            }
        }
        
        // Save to Firebase
        FirebaseManager firebaseManager = FirebaseManager.getInstance();
        
        // Check if user is signed in before saving
        if (firebaseManager.isUserSignedIn()) {
            Log.d("ShowdownClient", "User is signed in with ID: " + firebaseManager.getCurrentUser().getUid());
        } else {
            Log.d("ShowdownClient", "User is NOT signed in! Battle history will not be saved.");
        }
        
        boolean saved = firebaseManager.saveBattleHistory(
                opponent.replace("Guest", "Trainer"), // Clean up opponent name
                outcome,
                battleLog,
                playerTeam,
                opponentTeam
        );
        
        if (saved) {
            Log.d("ShowdownClient", "Battle history saved to Firebase");
        } else {
            Log.d("ShowdownClient", "Battle history not saved (user not signed in or guest mode)");
        }
        
        // Navigate back to main menu after saving battle history
        if (context instanceof BattleActivity) {
            BattleActivity battleActivity = (BattleActivity) context;
            
            // Add a small delay to ensure Firebase operation completes
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d("ShowdownClient", "Calling navigateToMainMenu on BattleActivity");
                battleActivity.navigateToMainMenu();
            }, 300);
        }
    }

    private void retrySearch() {
        if (battleRoomId == null && webSocket != null) {
            callback.onMessageReceived("🔄 Retrying randombattle search...");
            webSocket.send("|/search randombattle");
            
            // Schedule next retry
            scheduleRetry();
        }
    }
    
    /**
     * Schedule a retry for battle search
     */
    private void scheduleRetry() {
        // Cancel any existing retry
        stopRetryTimer();
        
        // Schedule a new retry
        handler.postDelayed(retryRunnable, RETRY_INTERVAL_MS);
    }
    
    /**
     * Stop the retry timer
     */
    private void stopRetryTimer() {
        handler.removeCallbacks(retryRunnable);
    }

    @Override
    public void onClosing(WebSocket ws, int code, String reason) {
        callback.onMessageReceived("❌ Closing: " + reason);
    }

    @Override
    public void onFailure(WebSocket ws, Throwable t, Response resp) {
        callback.onMessageReceived("💥 Error: " + t.getMessage());
    }
}
