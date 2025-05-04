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



    // Array of all available battle music tracks
    private static final String[] BATTLE_MUSIC_TRACKS = {
        "bw-rival",
        "bw-subway-trainer",
        "bw-trainer",
        "bw2-homika-dogars",
        "bw2-kanto-gym-leader",
        "bw2-rival",
        "colosseum-miror-b",
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
                Log.d(TAG, "Already playing " + musicTrack + ", not restarting");
                return;
            }

            // If we have a media player that's playing a different track, don't interrupt it
            // Let it finish naturally and the completion listener will handle the transition
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                Log.d(TAG, "Currently playing " + currentMusicTrack + ", will not interrupt");
                return;
            }

            // If we reach here, either there's no media player or it's not playing
            // So we can safely create a new one
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
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

                // Save the current track
                currentMusicTrack = musicTrack;
            });

            // Set up completion listener to play the next track only when the current one finishes
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Music track completed naturally: " + musicTrack + ", playing next track");
                // Only play the next track if we're still in battle mode and music is enabled
                if (inBattleMode && backgroundMusicEnabled) {
                    // Use a different track for better variety
                    playDifferentBattleMusic();
                }
            });

            // Set up error listener
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Media player error: " + what + ", " + extra);
                // Try a different track
                trySimpleRandomTrack();
                return true;
            });

        } catch (Exception e) {
            Log.e(TAG, "Error playing background music", e);
            // Try a simpler approach as fallback
            trySimpleRandomTrack();
        }
    }

    /**
     * Play a different battle music track than the current one
     * This ensures we don't play the same track twice in a row
     */
    private void playDifferentBattleMusic() {
        if (!backgroundMusicEnabled || !inBattleMode) {
            return;
        }

        try {
            // Select a random battle track that's different from the current one
            String randomTrack;
            int attempts = 0;
            int maxAttempts = 5; // Prevent infinite loop
            
            do {
                int randomIndex = (int) (Math.random() * BATTLE_MUSIC_TRACKS.length);
                randomTrack = BATTLE_MUSIC_TRACKS[randomIndex];
                attempts++;
            } while (randomTrack.equals(currentMusicTrack) && attempts < maxAttempts);

            // Play the selected track
            if (!randomTrack.equals(currentMusicTrack)) {
                Log.d(TAG, "Playing different track: " + randomTrack + " (previous was " + currentMusicTrack + ")");
                playBackgroundMusic(randomTrack);
            } else {
                // If we couldn't find a different track after max attempts, just play any track
                playRandomBattleMusic();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error playing different battle music", e);
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
     * Try playing a different random track if the current one fails - simplified version
     */
    private void trySimpleRandomTrack() {
        try {
            // Select a different random track
            String[] simpleTracks = {
                    "bw-rival",
                    "bw-subway-trainer",
                    "bw-trainer",
                    "bw2-homika-dogars",
                    "bw2-kanto-gym-leader",
                    "bw2-rival",
                    "colosseum-miror-b",
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
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }

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

            // Set up completion listener instead of looping
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Fallback track completed: " + randomTrack + ", playing next track");
                // Only play the next track if we're still in battle mode and music is enabled
                if (inBattleMode && backgroundMusicEnabled) {
                    playRandomBattleMusic();
                }
            });

            // Prepare and play
            mediaPlayer.prepare(); // Synchronous preparation
            mediaPlayer.start();

            // Save the current track
            currentMusicTrack = randomTrack;
            
            Log.d(TAG, "Successfully started fallback track: " + randomTrack);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play fallback track: " + e.getMessage());
            // Give up
            mediaPlayer = null;
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
            if (mediaPlayer == null) {
                // No media player exists, start a new one
                Log.d(TAG, "Starting music in updateMusicState - no media player exists");
                playBattleMusic();
            } else if (!mediaPlayer.isPlaying()) {
                // Media player exists but is not playing, try to resume
                try {
                    Log.d(TAG, "Resuming paused music in updateMusicState");
                    mediaPlayer.start();
                } catch (IllegalStateException e) {
                    // If resuming fails, create a new player
                    Log.e(TAG, "Error resuming music, starting new player", e);
                    playBattleMusic();
                }
            } else {
                // Media player exists and is playing, do nothing
                Log.d(TAG, "Music already playing in updateMusicState, doing nothing");
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
