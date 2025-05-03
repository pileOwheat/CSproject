package com.example.csproject;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Utility class to download sound files from the Pokémon Showdown API
 */
public class PokemonShowdownSoundDownloader {
    private static final String TAG = "PokemonShowdownSound";
    
    // Base URL for Pokémon Showdown audio files
    private static final String SHOWDOWN_AUDIO_BASE_URL = "https://play.pokemonshowdown.com/audio/";
    
    // Subdirectories for different types of audio
    private static final String SHOWDOWN_BGM_URL = SHOWDOWN_AUDIO_BASE_URL + "bgm/";
    private static final String SHOWDOWN_SFX_URL = SHOWDOWN_AUDIO_BASE_URL + "sfx/";
    private static final String SHOWDOWN_CRY_URL = SHOWDOWN_AUDIO_BASE_URL + "cries/";
    
    // Local cache directory name
    private static final String CACHE_DIR = "pokemon_sounds";
    
    /**
     * Download and play a Pokémon cry
     * 
     * @param context Android context
     * @param pokemonId Pokémon ID (National Dex number)
     * @param callback Callback to be notified when the sound is ready to play
     */
    public static void playPokemonCry(Context context, int pokemonId, SoundReadyCallback callback) {
        String pokemonName = getPokemonNameFromId(pokemonId);
        String url = SHOWDOWN_CRY_URL + pokemonName.toLowerCase() + ".mp3";
        downloadAndPlaySound(context, url, "cry_" + pokemonId, callback);
    }
    
    /**
     * Download and play a sound effect
     * 
     * @param context Android context
     * @param sfxName Name of the sound effect
     * @param callback Callback to be notified when the sound is ready to play
     */
    public static void playSoundEffect(Context context, String sfxName, SoundReadyCallback callback) {
        String url = SHOWDOWN_SFX_URL + sfxName + ".mp3";
        downloadAndPlaySound(context, url, "sfx_" + sfxName, callback);
    }
    
    /**
     * Download and play background music
     * 
     * @param context Android context
     * @param musicName Name of the music track
     * @param callback Callback to be notified when the sound is ready to play
     */
    public static void playBackgroundMusic(Context context, String musicName, SoundReadyCallback callback) {
        String url = SHOWDOWN_BGM_URL + musicName + ".mp3";
        downloadAndPlaySound(context, url, "bgm_" + musicName, callback);
    }
    
    /**
     * Download a sound file and play it
     * 
     * @param context Android context
     * @param url URL of the sound file
     * @param fileName Name to save the file as in the cache
     * @param callback Callback to be notified when the sound is ready to play
     */
    private static void downloadAndPlaySound(Context context, String url, String fileName, SoundReadyCallback callback) {
        // Check if the file is already cached
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        
        File soundFile = new File(cacheDir, fileName + ".mp3");
        
        if (soundFile.exists()) {
            // File already exists, play it
            playSound(soundFile.getAbsolutePath(), callback);
        } else {
            // Download the file
            new DownloadSoundTask(soundFile.getAbsolutePath(), callback).execute(url);
        }
    }
    
    /**
     * Play a sound file from the given path
     * 
     * @param filePath Path to the sound file
     * @param callback Callback to be notified when the sound is ready to play
     */
    private static void playSound(String filePath, SoundReadyCallback callback) {
        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
            });
            mediaPlayer.start();
            if (callback != null) {
                callback.onSoundReady(true);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error playing sound: " + e.getMessage());
            mediaPlayer.release();
            if (callback != null) {
                callback.onSoundReady(false);
            }
        }
    }
    
    /**
     * Get the Pokémon name from its ID
     * 
     * @param pokemonId Pokémon ID (National Dex number)
     * @return Pokémon name
     */
    private static String getPokemonNameFromId(int pokemonId) {
        // This is a simplified implementation
        // In a real app, you would have a complete mapping of Pokémon IDs to names
        
        switch (pokemonId) {
            case 1: return "Bulbasaur";
            case 4: return "Charmander";
            case 7: return "Squirtle";
            case 25: return "Pikachu";
            case 133: return "Eevee";
            // Add more mappings as needed
            default: return "pikachu"; // Default to Pikachu if unknown
        }
    }
    
    /**
     * AsyncTask to download a sound file
     */
    private static class DownloadSoundTask extends AsyncTask<String, Void, Boolean> {
        private String filePath;
        private SoundReadyCallback callback;
        
        public DownloadSoundTask(String filePath, SoundReadyCallback callback) {
            this.filePath = filePath;
            this.callback = callback;
        }
        
        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return false;
                }
                
                InputStream input = connection.getInputStream();
                FileOutputStream output = new FileOutputStream(filePath);
                
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                
                output.close();
                input.close();
                
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Error downloading sound: " + e.getMessage());
                return false;
            }
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                playSound(filePath, callback);
            } else if (callback != null) {
                callback.onSoundReady(false);
            }
        }
    }
    
    /**
     * Callback interface for sound download and playback
     */
    public interface SoundReadyCallback {
        void onSoundReady(boolean success);
    }
}
