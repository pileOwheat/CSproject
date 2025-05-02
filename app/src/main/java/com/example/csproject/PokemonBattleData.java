package com.example.csproject;

import android.util.Log;

/**
 * Class to store and manage Pokémon battle data
 * This handles parsing and storing information about Pokémon in battle
 */
public class PokemonBattleData {
    private static final String TAG = "PokemonBattleData";
    
    // Pokémon data
    private String name;
    private String details;
    private int level = 100; // Default level
    private int currentHP;
    private int maxHP;
    private boolean isFainted = false;
    
    // Position data
    private String position; // e.g., "p1a", "p2a"
    
    /**
     * Create a new PokemonBattleData instance
     * @param position The position identifier (e.g., "p1a", "p2a")
     * @param name The Pokémon's name
     * @param details The details string from the protocol (species, level, gender, etc.)
     * @param hpStatus The HP status string from the protocol (e.g., "100/100", "45/100")
     */
    public PokemonBattleData(String position, String name, String details, String hpStatus) {
        this.position = position;
        this.name = name;
        this.details = details;
        parseDetails(details);
        updateHP(hpStatus);
    }
    
    /**
     * Parse the details string to extract information like level
     * @param details The details string from the protocol
     */
    private void parseDetails(String details) {
        if (details == null || details.isEmpty()) return;
        
        // Parse level from details (format: "Species, L##, Gender, ...")
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
    
    /**
     * Update the HP values based on the HP status string
     * @param hpStatus The HP status string from the protocol
     */
    public void updateHP(String hpStatus) {
        if (hpStatus == null || hpStatus.isEmpty()) return;
        
        try {
            Log.d(TAG, "Updating HP with status: " + hpStatus);
            
            if (hpStatus.equals("0 fnt") || hpStatus.contains(" fnt")) {
                currentHP = 0;
                isFainted = true;
                Log.d(TAG, position + " " + name + " is fainted");
                return;
            }
            
            // Parse HP values (format: "current/max" or "current/100" for opponent)
            String[] hpParts = hpStatus.split("/");
            if (hpParts.length == 2) {
                // Handle possible formats like "100/100" or "100/100 par" (with status condition)
                String currentHpStr = hpParts[0].trim();
                String maxHpStr = hpParts[1].trim().split(" ")[0]; // Remove any status conditions
                
                currentHP = Integer.parseInt(currentHpStr);
                maxHP = Integer.parseInt(maxHpStr);
                
                Log.d(TAG, position + " " + name + " HP updated: " + currentHP + "/" + maxHP + 
                      " (" + getHPPercentage() + "%)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing HP status: " + hpStatus, e);
        }
    }
    
    /**
     * Set the Pokémon as fainted
     */
    public void setFainted() {
        currentHP = 0;
        isFainted = true;
        Log.d(TAG, position + " " + name + " was set to fainted state");
    }
    
    /**
     * Get the HP percentage (0-100)
     * @return The HP percentage
     */
    public int getHPPercentage() {
        if (maxHP == 0) return 0;
        return (int) (((float) currentHP / maxHP) * 100);
    }
    
    // Getters
    public String getName() {
        return name;
    }
    
    public int getLevel() {
        return level;
    }
    
    public String getPosition() {
        return position;
    }
    
    public boolean isFainted() {
        return isFainted;
    }
    
    public int getCurrentHP() {
        return currentHP;
    }
    
    public int getMaxHP() {
        return maxHP;
    }
}
