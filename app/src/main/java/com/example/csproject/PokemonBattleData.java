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
    
    /**
     * Get the Pokémon's National Dex number based on its name
     * @return The dex number, or 0 if it couldn't be determined
     */
    public int getDexNumber() {
        // This is a simplified implementation - in a real app, you would have a complete mapping
        // of Pokémon names to dex numbers or use an API
        
        // Extract species name from details if available
        String speciesName = name;
        if (details != null && !details.isEmpty()) {
            int commaIndex = details.indexOf(",");
            if (commaIndex > 0) {
                speciesName = details.substring(0, commaIndex).trim();
            }
        }
        
        // Handle special forms by using the base form's cry
        if (speciesName.contains("-")) {
            speciesName = speciesName.substring(0, speciesName.indexOf("-"));
        }
        
        // Convert name to lowercase for case-insensitive comparison
        String pokemonName = speciesName.toLowerCase();
        
        // Map of some common Pokémon names to their dex numbers
        // This is just a small sample - a real implementation would have all Pokémon
        switch (pokemonName) {
            case "bulbasaur": return 1;
            case "ivysaur": return 2;
            case "venusaur": return 3;
            case "charmander": return 4;
            case "charmeleon": return 5;
            case "charizard": return 6;
            case "squirtle": return 7;
            case "wartortle": return 8;
            case "blastoise": return 9;
            case "pikachu": return 25;
            case "raichu": return 26;
            case "nidoran-f": return 29;
            case "nidoran-m": return 32;
            case "jigglypuff": return 39;
            case "zubat": return 41;
            case "oddish": return 43;
            case "meowth": return 52;
            case "psyduck": return 54;
            case "growlithe": return 58;
            case "machop": return 66;
            case "tentacool": return 72;
            case "geodude": return 74;
            case "ponyta": return 77;
            case "slowpoke": return 79;
            case "magnemite": return 81;
            case "farfetchd": return 83;
            case "doduo": return 84;
            case "seel": return 86;
            case "grimer": return 88;
            case "shellder": return 90;
            case "gastly": return 92;
            case "onix": return 95;
            case "drowzee": return 96;
            case "krabby": return 98;
            case "voltorb": return 100;
            case "exeggcute": return 102;
            case "cubone": return 104;
            case "hitmonlee": return 106;
            case "hitmonchan": return 107;
            case "koffing": return 109;
            case "rhyhorn": return 111;
            case "chansey": return 113;
            case "tangela": return 114;
            case "kangaskhan": return 115;
            case "horsea": return 116;
            case "goldeen": return 118;
            case "staryu": return 120;
            case "scyther": return 123;
            case "jynx": return 124;
            case "electabuzz": return 125;
            case "magmar": return 126;
            case "pinsir": return 127;
            case "tauros": return 128;
            case "magikarp": return 129;
            case "gyarados": return 130;
            case "lapras": return 131;
            case "ditto": return 132;
            case "eevee": return 133;
            case "vaporeon": return 134;
            case "jolteon": return 135;
            case "flareon": return 136;
            case "porygon": return 137;
            case "omanyte": return 138;
            case "kabuto": return 140;
            case "aerodactyl": return 142;
            case "snorlax": return 143;
            case "articuno": return 144;
            case "zapdos": return 145;
            case "moltres": return 146;
            case "dratini": return 147;
            case "dragonair": return 148;
            case "dragonite": return 149;
            case "mewtwo": return 150;
            case "mew": return 151;
            // Gen 2 starters
            case "chikorita": return 152;
            case "cyndaquil": return 155;
            case "totodile": return 158;
            // Other popular Pokémon
            case "lugia": return 249;
            case "ho-oh": return 250;
            case "celebi": return 251;
            default:
                Log.d(TAG, "Unknown Pokémon for cry: " + pokemonName);
                return 0;
        }
    }
}
