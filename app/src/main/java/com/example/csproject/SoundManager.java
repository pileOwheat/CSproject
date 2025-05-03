package com.example.csproject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;
import android.util.SparseIntArray;
import android.widget.Toast;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * SoundManager handles all sound effects and background music for the Pokémon battle app.
 * It integrates with the Pokémon Showdown API to provide authentic Pokémon sound effects.
 */
public class SoundManager {
    private static final String TAG = "SoundManager";
    
    // Singleton instance
    private static SoundManager instance;
    
    // Context
    private Context context;
    
    // Sound effects
    private SoundPool soundPool;
    private SparseIntArray soundMap;
    
    // Background music
    private MediaPlayer mediaPlayer;
    private String currentMusicTrack;
    private boolean isResettingMusic = false;
    
    // Settings
    private boolean soundEffectsEnabled = true;
    private boolean backgroundMusicEnabled = true;
    private float volume = 0.8f;
    private boolean inBattleMode = false;
    
    // Sound effect constants
    public static final int SFX_CLICK = 1;
    public static final int SFX_BATTLE_START = 2;
    public static final int SFX_ATTACK = 3;
    public static final int SFX_SUPER_EFFECTIVE = 4;
    public static final int SFX_NOT_EFFECTIVE = 5;
    public static final int SFX_VICTORY = 6;
    public static final int SFX_DEFEAT = 7;
    
    // Mapping of sound effect IDs to Pokémon Showdown sound names
    private static final Map<Integer, String> SHOWDOWN_SFX_MAP = new HashMap<>();
    static {
        SHOWDOWN_SFX_MAP.put(SFX_CLICK, "click");
        SHOWDOWN_SFX_MAP.put(SFX_BATTLE_START, "mega");
        SHOWDOWN_SFX_MAP.put(SFX_ATTACK, "attack");
        SHOWDOWN_SFX_MAP.put(SFX_SUPER_EFFECTIVE, "superhit");
        SHOWDOWN_SFX_MAP.put(SFX_NOT_EFFECTIVE, "nothit");
        SHOWDOWN_SFX_MAP.put(SFX_VICTORY, "victory");
        SHOWDOWN_SFX_MAP.put(SFX_DEFEAT, "faint");
    }
    
    // Pokémon Showdown API URLs
    private static final String SHOWDOWN_AUDIO_BASE_URL = "https://play.pokemonshowdown.com/audio/";
    private static final String SHOWDOWN_SFX_URL = SHOWDOWN_AUDIO_BASE_URL + "sfx/";
    private static final String SHOWDOWN_CRY_URL = SHOWDOWN_AUDIO_BASE_URL + "cries/";
    
    // Pokémon Showdown background music tracks
    public static final String BGM_BATTLE = "battle-gen8";
    public static final String BGM_BATTLE_XY = "battle-xy";
    public static final String BGM_BATTLE_SM = "battle-sm";
    public static final String BGM_BATTLE_DPPT = "battle-dppt";
    public static final String BGM_BATTLE_RSE = "battle-rse";
    public static final String BGM_BATTLE_GSC = "battle-gsc";
    public static final String BGM_BATTLE_RBY = "battle-rby";
    
    // Array of all available battle music tracks
    private static final String[] BATTLE_MUSIC_TRACKS = {
        BGM_BATTLE,          // Gen 8 (Sword/Shield)
        BGM_BATTLE_XY,       // X/Y
        BGM_BATTLE_SM,       // Sun/Moon
        BGM_BATTLE_DPPT,     // Diamond/Pearl/Platinum
        BGM_BATTLE_RSE,      // Ruby/Sapphire/Emerald
        BGM_BATTLE_GSC,      // Gold/Silver/Crystal
        BGM_BATTLE_RBY,      // Red/Blue/Yellow
        "battle-bw",         // Black/White
        "battle-bw2",        // Black 2/White 2
        "battle-colosseum",  // Pokémon Colosseum
        "battle-dpp-wild",   // Diamond/Pearl/Platinum Wild Battle
        "battle-oras",       // Omega Ruby/Alpha Sapphire
        "battle-swsh-gym-leader", // Sword/Shield Gym Leader
        "battle-swsh-wild",  // Sword/Shield Wild Battle
        "battle-usum-legend", // Ultra Sun/Ultra Moon Legendary Battle
        "battle-usum-ultra-beast", // Ultra Sun/Ultra Moon Ultra Beast Battle
        "battle-xd",         // Pokémon XD: Gale of Darkness
        "battle-champion-rse", // Ruby/Sapphire/Emerald Champion Battle
        "battle-frontier-brain", // Frontier Brain Battle
        "battle-gym-leader-gen1", // Gen 1 Gym Leader
        "battle-gym-leader-gen3" // Gen 3 Gym Leader
    };
    
    /**
     * Get the singleton instance of SoundManager
     */
    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Private constructor to enforce singleton pattern
     */
    private SoundManager(Context context) {
        this.context = context;
        
        // Initialize sound pool for local sound effects
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(10)
                    .setAudioAttributes(attributes)
                    .build();
        } else {
            soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        }
        
        // Initialize sound map
        soundMap = new SparseIntArray();
        
        // Load settings
        loadSettings();
        
        // Load basic sound effects (for UI interactions)
        loadBasicSoundEffects();
    }
    
    /**
     * Load settings from SharedPreferences
     */
    private void loadSettings() {
        SharedPreferences prefs = context.getSharedPreferences("PokemonBattlePrefs", Context.MODE_PRIVATE);
        soundEffectsEnabled = prefs.getBoolean("sound_effects", true);
        backgroundMusicEnabled = prefs.getBoolean("background_music", true);
        volume = prefs.getInt("sound_volume", 80) / 100.0f;
    }
    
    /**
     * Update settings from SharedPreferences
     */
    public void updateSettings() {
        SharedPreferences prefs = context.getSharedPreferences("PokemonBattlePrefs", Context.MODE_PRIVATE);
        soundEffectsEnabled = prefs.getBoolean("sound_effects", true);
        backgroundMusicEnabled = prefs.getBoolean("background_music", true);
        
        // Update volume from preferences (stored as 0-100, convert to 0.0-1.0)
        int volumePercent = prefs.getInt("sound_volume", 80);
        volume = volumePercent / 100.0f;
        
        // Apply volume to current media player if it exists
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(volume, volume);
        }
        
        // If background music is disabled, stop any playing music
        if (!backgroundMusicEnabled && mediaPlayer != null) {
            stopBackgroundMusic();
        }
        
        Log.d(TAG, "Settings updated: sound effects=" + soundEffectsEnabled + 
              ", background music=" + backgroundMusicEnabled + 
              ", volume=" + volume);
    }
    
    /**
     * Set the volume level (0.0 to 1.0)
     */
    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        
        // Apply to current media player if it exists
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(this.volume, this.volume);
        }
        
        // Save the setting
        SharedPreferences.Editor editor = context.getSharedPreferences("PokemonBattlePrefs", Context.MODE_PRIVATE).edit();
        editor.putInt("sound_volume", (int)(this.volume * 100));
        editor.apply();
        
        Log.d(TAG, "Volume set to " + this.volume);
    }
    
    /**
     * Enable or disable sound effects
     */
    public void setSoundEffectsEnabled(boolean enabled) {
        this.soundEffectsEnabled = enabled;
        
        // Save the setting
        SharedPreferences.Editor editor = context.getSharedPreferences("PokemonBattlePrefs", Context.MODE_PRIVATE).edit();
        editor.putBoolean("sound_effects", enabled);
        editor.apply();
        
        Log.d(TAG, "Sound effects " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Enable or disable background music
     */
    public void setBackgroundMusicEnabled(boolean enabled) {
        backgroundMusicEnabled = enabled;
        
        // Save the setting
        SharedPreferences prefs = context.getSharedPreferences("PokemonBattlePrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("background_music", enabled).apply();
        
        Log.d(TAG, "Background music enabled: " + enabled);
        
        // Only play/pause music if we're in battle mode
        if (inBattleMode) {
            if (enabled) {
                // If we have a paused MediaPlayer, resume it
                if (mediaPlayer != null) {
                    try {
                        if (!mediaPlayer.isPlaying()) {
                            mediaPlayer.start();
                            Log.d(TAG, "Resumed paused background music");
                        }
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Error resuming music", e);
                        // If resuming fails, try to restart
                        playBattleMusic();
                    }
                } else {
                    // If no media player exists, start a new one
                    Log.d(TAG, "Starting new background music");
                    playBattleMusic();
                }
            } else {
                // Pause the music instead of stopping it
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    Log.d(TAG, "Paused background music");
                }
            }
        } else {
            Log.d(TAG, "Not in battle mode, music setting saved but not applied");
        }
    }
    
    /**
     * Set whether we're currently in battle mode
     * Music will only play automatically when in battle mode
     * @param inBattle true if in battle, false otherwise
     */
    public void setInBattleMode(boolean inBattle) {
        this.inBattleMode = inBattle;
        
        Log.d(TAG, "Battle mode set to: " + inBattle);
        
        // If we're entering battle mode and music is enabled, start playing
        if (inBattle && backgroundMusicEnabled) {
            if (mediaPlayer == null || !mediaPlayer.isPlaying()) {
                Log.d(TAG, "Starting music because entering battle mode");
                playBattleMusic();
            }
        } else if (!inBattle && mediaPlayer != null && mediaPlayer.isPlaying()) {
            // If we're leaving battle mode, pause the music
            Log.d(TAG, "Pausing music because leaving battle mode");
            pauseBackgroundMusic();
        }
    }
    
    /**
     * Load basic sound effects from resources
     */
    private void loadBasicSoundEffects() {
        try {
            // In a real implementation, we would load actual sound files
            // For now, we'll simulate sound loading since we only have placeholder text files
            // soundMap.put(SFX_CLICK, soundPool.load(context, R.raw.click_sound, 1));
            
            // Simulate successful loading of sound
            soundMap.put(SFX_CLICK, 1);
            Log.d(TAG, "Simulated loading of click sound effect");
        } catch (Exception e) {
            Log.e(TAG, "Error loading sound effects: " + e.getMessage());
        }
    }
    
    /**
     * Play a sound effect
     */
    public void playSoundEffect(int soundId) {
        if (!soundEffectsEnabled) {
            Log.d(TAG, "Sound effects disabled, not playing sound: " + soundId);
            return;
        }
        
        Log.d(TAG, "Playing sound effect: " + soundId);
        
        // First try to play from local resources
        int soundResourceId = soundMap.get(soundId, -1);
        if (soundResourceId != -1) {
            // In a real implementation, we would play the actual sound
            // soundPool.play(soundResourceId, volume, volume, 1, 0, 1.0f);
            
            // For now, just log that we would play the sound
            Log.d(TAG, "Playing sound effect from local resources: " + soundId + " at volume " + volume);
            
            return;
        }
        
        // If not available locally, stream from Pokémon Showdown API
        String showdownSfxName = SHOWDOWN_SFX_MAP.get(soundId);
        if (showdownSfxName != null) {
            String url = SHOWDOWN_SFX_URL + showdownSfxName + ".mp3";
            Log.d(TAG, "Streaming sound effect from URL: " + url);
            streamSoundWithoutToast(url);
        } else {
            Log.e(TAG, "Unknown sound effect ID: " + soundId);
        }
    }
    
    /**
     * Play a Pokémon cry
     */
    public void playPokemonCry(int pokemonId) {
        if (!soundEffectsEnabled) return;
        
        String pokemonName = getPokemonNameFromId(pokemonId);
        if (pokemonName != null) {
            String url = SHOWDOWN_CRY_URL + pokemonName.toLowerCase() + ".mp3";
            streamSoundWithoutToast(url);
        }
    }
    
    /**
     * Play a Pokémon cry by name
     * @param pokemonName The name of the Pokémon
     */
    public void playPokemonCryByName(String pokemonName) {
        if (!soundEffectsEnabled) {
            Log.d(TAG, "Sound effects disabled, not playing cry for: " + pokemonName);
            return;
        }
        
        if (pokemonName != null && !pokemonName.isEmpty()) {
            // Format the Pokémon name for the URL
            String formattedName = pokemonName.toLowerCase()
                    .replace(" ", "")
                    .replace("-", "")
                    .replace(".", "")
                    .replace("'", "")
                    .replace(":", "");
                    
            String url = SHOWDOWN_CRY_URL + formattedName + ".mp3";
            Log.d(TAG, "Playing cry for: " + pokemonName + " from URL: " + url);
            streamSoundWithoutToast(url);
        }
    }
    
    /**
     * Get the Pokémon name from its ID
     */
    private String getPokemonNameFromId(int pokemonId) {
        // This is a simplified implementation
        // In a real app, you would have a complete mapping of Pokémon IDs to names
        switch (pokemonId) {
            case 1: return "bulbasaur";
            case 4: return "charmander";
            case 7: return "squirtle";
            case 25: return "pikachu";
            case 133: return "eevee";
            // Add more mappings as needed
            default: return "pikachu"; // Default to Pikachu if unknown
        }
    }
    
    /**
     * Play battle music
     */
    public void playBattleMusic() {
        // Always check if background music is enabled
        if (!backgroundMusicEnabled) {
            return;
        }
        
        // Only play if in battle mode
        if (!inBattleMode) {
            Log.d(TAG, "Not playing battle music because not in battle mode");
            return;
        }
        
        Log.d(TAG, "Playing battle music in battle mode");
        // Play a random battle track
        playRandomBattleMusic();
    }
    
    /**
     * Play a random battle music track
     */
    private void playRandomBattleMusic() {
        if (!backgroundMusicEnabled) {
            return;
        }
        
        try {
            // Select a random battle track
            int randomIndex = (int) (Math.random() * BATTLE_MUSIC_TRACKS.length);
            String randomTrack = BATTLE_MUSIC_TRACKS[randomIndex];
            
            // Play the selected track
            playBackgroundMusic(randomTrack);
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing random battle music", e);
            // Try a simpler approach as fallback
            trySimpleRandomTrack();
        }
    }
    
    /**
     * Play background music from a URL
     * @param musicTrack The music track name
     */
    public void playBackgroundMusic(String musicTrack) {
        // Always check if background music is enabled and we're in battle mode
        if (!backgroundMusicEnabled || !inBattleMode) {
            Log.d(TAG, "Not playing background music: enabled=" + backgroundMusicEnabled + ", inBattle=" + inBattleMode);
            return;
        }
        
        try {
            // Check if we're already playing this track
            if (mediaPlayer != null && currentMusicTrack != null && currentMusicTrack.equals(musicTrack) && mediaPlayer.isPlaying()) {
                Log.d(TAG, "Already playing " + musicTrack);
                return;
            }
            
            // Stop any existing music
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            }
            
            // Create a new media player
            mediaPlayer = new MediaPlayer();
            
            // Set up the media player
            String url = SHOWDOWN_AUDIO_BASE_URL + musicTrack + ".mp3";
            Log.d(TAG, "Playing background music: " + url);
            
            // Set the audio attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                );
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            
            // Set the data source and prepare
            mediaPlayer.setDataSource(url);
            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(volume, volume);
            
            // Use asynchronous preparation to avoid UI delays
            mediaPlayer.prepareAsync();
            
            // Set up prepared listener to start playing when ready
            mediaPlayer.setOnPreparedListener(mp -> {
                // Start playing immediately if enabled and in battle mode
                if (backgroundMusicEnabled && inBattleMode) {
                    mp.start();
                    Log.d(TAG, "Background music started: " + musicTrack);
                }
            });
            
            // Set up error listener
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Media player error: " + what + ", " + extra);
                // Try a different track
                trySimpleRandomTrack();
                return true;
            });
            
            // Save the current track
            currentMusicTrack = musicTrack;
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing background music", e);
            // Try a simpler approach as fallback
            trySimpleRandomTrack();
        }
    }
    
    /**
     * Stream a sound from a URL without showing a toast notification
     */
    private void streamSoundWithoutToast(String url) {
        try {
            Log.d(TAG, "Streaming sound from URL: " + url);
            
            // Create a new media player for this sound
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );
            
            // Set the data source to the URL
            player.setDataSource(url);
            
            // Set volume based on user settings
            player.setVolume(volume, volume);
            
            // Prepare the player asynchronously
            player.prepareAsync();
            
            // Start playing when prepared
            player.setOnPreparedListener(MediaPlayer::start);
            
            // Release resources when playback is complete
            player.setOnCompletionListener(MediaPlayer::release);
            
            // Handle errors
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Error streaming sound: " + what + ", " + extra);
                mp.release();
                return true;
            });
        } catch (Exception e) {
            Log.e(TAG, "Error streaming sound: " + e.getMessage());
        }
    }
    
    /**
     * Stream a sound from a URL
     */
    private void streamSound(String url, String description) {
        try {
            // Create a new media player for this sound
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );
            
            // Set the data source to the URL
            player.setDataSource(url);
            
            // Set volume based on user settings
            player.setVolume(volume, volume);
            
            // Prepare the player asynchronously
            player.prepareAsync();
            
            // Start playing when prepared
            player.setOnPreparedListener(MediaPlayer::start);
            
            // Release resources when playback is complete
            player.setOnCompletionListener(MediaPlayer::release);
            
            // Handle errors
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Error streaming sound: " + what + ", " + extra);
                mp.release();
                return true;
            });
        } catch (Exception e) {
            Log.e(TAG, "Error streaming sound: " + e.getMessage());
        }
    }
    
    /**
     * Try playing a different random track if the current one fails - simplified version
     */
    private void trySimpleRandomTrack() {
        try {
            // Select a different random track
            String[] simpleTracks = {
                // Original tracks
                "colosseum-miror-b",
                "battle-gen8",
                "battle-xy",
                "battle-sm",
                "battle-dppt",
                "battle-rse",
                
                // Additional tracks
                "bw-rival",
                "bw-subway-trainer",
                "bw-trainer",
                "bw2-homika-dogars",
                "bw2-kanto-gym-leader",
                "bw2-rival",
                "dpp-rival",
                "dpp-trainer",
                "hgss-johto-trainer",
                "hgss-kanto-trainer",
                "oras-rival",
                "oras-trainer",
                "sm-rival",
                "sm-trainer",
                "spl-elite4",
                "xd-miror-b",
                "xy-rival",
                "xy-trainer"
            };
            
            int randomIndex = (int) (Math.random() * simpleTracks.length);
            String randomTrack = simpleTracks[randomIndex];
            
            Log.d(TAG, "Trying simple fallback track: " + randomTrack);
            
            // Play the selected track
            String url = SHOWDOWN_AUDIO_BASE_URL + randomTrack + ".mp3";
            
            // Stop any existing media player
            stopBackgroundMusic();
            
            // Create a new media player
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );
            
            // Set the data source to the URL
            mediaPlayer.setDataSource(url);
            
            // Set volume based on user settings
            mediaPlayer.setVolume(volume, volume);
            
            // Loop the background music
            mediaPlayer.setLooping(true);
            
            // Prepare and play
            mediaPlayer.prepare(); // Synchronous preparation
            mediaPlayer.start();
            
            Log.d(TAG, "Successfully started fallback track: " + randomTrack);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play fallback track: " + e.getMessage());
            // Give up
            mediaPlayer = null;
        }
    }
    
    /**
     * Reset the background music if it's having issues
     */
    private void resetBackgroundMusic() {
        if (isResettingMusic) return;
        
        isResettingMusic = true;
        
        try {
            // Save the current position and track
            String currentTrack = this.currentMusicTrack;
            
            // Stop and release the current player
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }
            
            // Create a new player and restart the music
            if (currentTrack != null && backgroundMusicEnabled) {
                Log.d(TAG, "Resetting background music: " + currentTrack);
                playBackgroundMusic(currentTrack);
            } else {
                // If we don't have a track, try playing a random one
                playBattleMusic();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resetting background music: " + e.getMessage());
            // Try a completely new track
            playBattleMusic();
        } finally {
            isResettingMusic = false;
        }
    }

    /**
     * Manually force music to play or pause based on current settings
     * This can be called from the battle activity to ensure music state is correct
     */
    public void updateMusicState() {
        Log.d(TAG, "Updating music state: inBattleMode=" + inBattleMode + ", backgroundMusicEnabled=" + backgroundMusicEnabled);
        
        if (inBattleMode && backgroundMusicEnabled) {
            // We should be playing music
            if (mediaPlayer == null || !mediaPlayer.isPlaying()) {
                Log.d(TAG, "Starting music in updateMusicState");
                playBattleMusic();
            }
        } else if (!backgroundMusicEnabled && mediaPlayer != null && mediaPlayer.isPlaying()) {
            // We should not be playing music
            Log.d(TAG, "Pausing music in updateMusicState");
            pauseBackgroundMusic();
        }
    }

    /**
     * Stop background music
     */
    public void stopBackgroundMusic() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
            currentMusicTrack = null;
        }
    }
    
    /**
     * Pause background music without releasing the MediaPlayer
     */
    public void pauseBackgroundMusic() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }
    
    /**
     * Release all resources
     */
    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        
        stopBackgroundMusic();
        
        instance = null;
    }
}
