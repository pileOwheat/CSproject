package com.example.csproject;

import android.content.Context;
import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.BuildConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.auth.ActionCodeSettings;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Manager class for handling all Firebase operations
 */
public class FirebaseManager {
    private static final String TAG = "FirebaseManager";
    
    // Singleton instance
    private static FirebaseManager instance;
    
    // Firebase instances
    private final FirebaseAuth mAuth;
    private final DatabaseReference mDatabase;
    private Context mContext;
    
    // Interface for authentication callbacks
    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onFailure(Exception e);
    }
    
    // Interface for battle history callbacks
    public interface BattleHistoryCallback {
        void onBattleHistoryLoaded(List<BattleHistory> battleHistories);
        void onFailure(Exception e);
    }
    
    // Private constructor for singleton pattern
    private FirebaseManager() {
        FirebaseAuth auth = null;
        DatabaseReference database = null;
        
        try {
            // First check if Firebase is already initialized
            FirebaseApp defaultApp = null;
            try {
                defaultApp = FirebaseApp.getInstance();
            } catch (IllegalStateException e) {
                // Firebase not initialized yet
                Log.d(TAG, "Firebase not initialized yet, initializing now");
            }
            
            // Get Firebase Auth instance
            auth = FirebaseAuth.getInstance();
            
            // Connect to Firebase emulator for local development
            // This bypasses the need for reCAPTCHA verification
            if (BuildConfig.DEBUG) {
                // Use emulator for authentication in debug builds
                auth.useEmulator("10.0.2.2", 9099);
                Log.d(TAG, "Using Firebase Auth emulator at 10.0.2.2:9099");
            }
            
            // Set the database URL explicitly
            FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
            
            // Enable offline capabilities if not already set
            try {
                firebaseDatabase.setPersistenceEnabled(true);
            } catch (Exception e) {
                // Persistence might already be enabled, which throws an exception
                Log.d(TAG, "Firebase persistence already enabled or error: " + e.getMessage());
            }
            
            // Use the specific database URL from your Firebase project
            String databaseUrl = "https://csproject-e9cff-default-rtdb.firebaseio.com/";
            try {
                firebaseDatabase = FirebaseDatabase.getInstance(databaseUrl);
                
                // We're NOT connecting to the Firebase Database emulator
                // This ensures battle history is saved to and retrieved from the real database
                
                Log.d(TAG, "Connected to Firebase database at: " + databaseUrl);
            } catch (Exception e) {
                Log.e(TAG, "Error connecting to Firebase database: " + e.getMessage());
            }
            
            database = firebaseDatabase.getReference();
            Log.d(TAG, "Firebase Manager initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase Manager: " + e.getMessage());
            // Initialize with defaults to avoid null pointer exceptions
            if (auth == null) {
                auth = FirebaseAuth.getInstance();
            }
            if (database == null) {
                database = FirebaseDatabase.getInstance().getReference();
            }
        }
        
        // Assign to final fields
        mAuth = auth;
        mDatabase = database;
    }
    
    // Get singleton instance
    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }
    
    // Initialize with context
    public void initialize(Context context) {
        this.mContext = context.getApplicationContext();
        Log.d(TAG, "Firebase Manager initialized with context");
    }
    
    /**
     * Check if user is currently signed in
     * @return true if user is signed in, false otherwise
     */
    public boolean isUserSignedIn() {
        return mAuth.getCurrentUser() != null;
    }
    
    /**
     * Get current user
     * @return current FirebaseUser or null if not signed in
     */
    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }
    
    /**
     * Sign in with email and password
     * @param email user email
     * @param password user password
     * @param callback callback to handle success or failure
     */
    public void signIn(String email, String password, AuthCallback callback) {
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "signInWithEmail:success");
                    callback.onSuccess(mAuth.getCurrentUser());
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.getException());
                    callback.onFailure(task.getException());
                }
            });
    }
    
    /**
     * Create a new user with email and password
     * @param email user email
     * @param password user password
     * @param callback callback to handle success or failure
     */
    public void createAccount(String email, String password, AuthCallback callback) {
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "createUserWithEmail:success");
                    callback.onSuccess(mAuth.getCurrentUser());
                } else {
                    Log.w(TAG, "createUserWithEmail:failure", task.getException());
                    callback.onFailure(task.getException());
                }
            });
    }
    
    /**
     * Sign out the current user
     */
    public void signOut() {
        mAuth.signOut();
    }
    
    /**
     * Save battle history to Firebase
     * @param opponentName name of the opponent
     * @param outcome result of the battle ("win", "loss", or "tie")
     * @param battleLog complete battle log text
     * @param playerTeam player's team information
     * @param opponentTeam opponent's team information
     * @return true if user is signed in and battle can be saved, false otherwise
     */
    public boolean saveBattleHistory(String opponentName, String outcome, String battleLog, 
                                  String playerTeam, String opponentTeam) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "Cannot save battle history: User not signed in");
            return false;
        }
        
        String userId = currentUser.getUid();
        Log.d(TAG, "Saving battle history for user ID: " + userId);
        
        // Generate a unique battle ID
        String battleId = mDatabase.child("users").child(userId).child("battles").push().getKey();
        Log.d(TAG, "Generated battle ID: " + battleId);
        
        BattleHistory battleHistory = new BattleHistory(
                battleId,
                userId,
                opponentName,
                outcome,
                new Date(),
                battleLog,
                playerTeam,
                opponentTeam
        );
        
        // Save battle under the user's ID for easier retrieval
        String dbPath = "users/" + userId + "/battles/" + battleId;
        Log.d(TAG, "Saving battle to path: " + dbPath);
        
        mDatabase.child("users").child(userId).child("battles").child(battleId).setValue(battleHistory)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Battle history saved successfully to Firebase at " + dbPath);
                    // Log the actual data that was saved
                    Log.d(TAG, "Battle data: opponent=" + opponentName + ", outcome=" + outcome + 
                          ", userId=" + userId + ", timestamp=" + new Date());
                })
                .addOnFailureListener(e -> Log.w(TAG, "Error saving battle history", e));
        
        return true;
    }
    
    /**
     * Get battle history for current user
     * @param callback callback to handle loaded battle histories
     */
    public void getBattleHistory(BattleHistoryCallback callback) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "Cannot get battle history: User not signed in");
            callback.onFailure(new Exception("User not signed in"));
            return;
        }
        
        String userId = currentUser.getUid();
        Log.d(TAG, "Getting battle history for user ID: " + userId);
        
        // First, check the new path structure
        DatabaseReference userBattlesRef = mDatabase.child("users").child(userId).child("battles");
        Log.d(TAG, "Checking new database path: " + userBattlesRef.toString());
        
        userBattlesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "New path data snapshot received, has children: " + dataSnapshot.hasChildren() + ", child count: " + dataSnapshot.getChildrenCount());
                
                // If we have data in the new path, use it
                if (dataSnapshot.hasChildren()) {
                    processBattleHistorySnapshot(dataSnapshot, userId, callback);
                } else {
                    // Otherwise, check the old path
                    Log.d(TAG, "No battle history found in new path, checking old path");
                    checkOldBattlePath(userId, callback);
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load battle histories from new path: " + databaseError.getMessage());
                // Try the old path as a fallback
                checkOldBattlePath(userId, callback);
            }
        });
    }
    
    /**
     * Check the old database path for battle history
     */
    private void checkOldBattlePath(String userId, BattleHistoryCallback callback) {
        // Instead of using a query which requires an index, get all battles and filter manually
        DatabaseReference battlesRef = mDatabase.child("battles");
        Log.d(TAG, "Checking old database path: " + battlesRef.toString());
        
        battlesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Old path data snapshot received, has children: " + dataSnapshot.hasChildren() + ", child count: " + dataSnapshot.getChildrenCount());
                
                List<BattleHistory> battleHistories = new ArrayList<>();
                
                // Log all the keys found
                StringBuilder keysFound = new StringBuilder("All battle keys found: ");
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    keysFound.append(snapshot.getKey()).append(", ");
                    
                    try {
                        // Check if this battle belongs to the current user
                        Map<String, Object> rawData = (Map<String, Object>) snapshot.getValue();
                        if (rawData != null && userId.equals(rawData.get("userId"))) {
                            Log.d(TAG, "Found battle belonging to current user: " + snapshot.getKey());
                            
                            // Try to convert to BattleHistory object
                            BattleHistory battleHistory = snapshot.getValue(BattleHistory.class);
                            if (battleHistory != null) {
                                Log.d(TAG, "Successfully parsed battle history: " + battleHistory.getBattleId() + ", opponent: " + battleHistory.getOpponentName());
                                battleHistories.add(battleHistory);
                            } else {
                                Log.w(TAG, "Failed to parse battle history, creating manually");
                                
                                // Manually create a BattleHistory object from the raw data
                                try {
                                    String battleId = (String) rawData.get("battleId");
                                    String opponentName = (String) rawData.get("opponentName");
                                    String outcome = (String) rawData.get("outcome");
                                    Object timestampObj = rawData.get("timestamp");
                                    Date timestamp = null;
                                    if (timestampObj instanceof Long) {
                                        timestamp = new Date((Long) timestampObj);
                                    } else if (timestampObj instanceof Map) {
                                        // Handle Firebase server timestamp
                                        Map<String, Object> timestampMap = (Map<String, Object>) timestampObj;
                                        if (timestampMap.containsKey("time")) {
                                            timestamp = new Date((Long) timestampMap.get("time"));
                                        }
                                    }
                                    if (timestamp == null) {
                                        timestamp = new Date(); // Fallback
                                    }
                                    
                                    String battleLog = (String) rawData.get("battleLog");
                                    String playerTeam = (String) rawData.get("playerTeam");
                                    String opponentTeam = (String) rawData.get("opponentTeam");
                                    
                                    BattleHistory manualBattleHistory = new BattleHistory(
                                            battleId,
                                            userId,
                                            opponentName,
                                            outcome,
                                            timestamp,
                                            battleLog,
                                            playerTeam,
                                            opponentTeam
                                    );
                                    
                                    Log.d(TAG, "Manually created battle history: " + manualBattleHistory.getBattleId());
                                    battleHistories.add(manualBattleHistory);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error creating battle history from raw data", e);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing battle: " + e.getMessage(), e);
                    }
                }
                
                Log.d(TAG, keysFound.toString());
                Log.d(TAG, "Loaded " + battleHistories.size() + " battle histories from old path");
                callback.onBattleHistoryLoaded(battleHistories);
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load battle histories from old path: " + databaseError.getMessage());
                callback.onFailure(databaseError.toException());
            }
        });
    }
    
    /**
     * Process a data snapshot containing battle history
     */
    private void processBattleHistorySnapshot(DataSnapshot dataSnapshot, String userId, BattleHistoryCallback callback) {
        List<BattleHistory> battleHistories = new ArrayList<>();
        
        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
            Log.d(TAG, "Processing battle snapshot with key: " + snapshot.getKey());
            try {
                BattleHistory battleHistory = snapshot.getValue(BattleHistory.class);
                if (battleHistory != null) {
                    Log.d(TAG, "Successfully parsed battle history: " + battleHistory.getBattleId() + ", opponent: " + battleHistory.getOpponentName());
                    battleHistories.add(battleHistory);
                } else {
                    Log.w(TAG, "Failed to parse battle history, snapshot value is null");
                    // Try to get the raw data to see what's there
                    Map<String, Object> rawData = (Map<String, Object>) snapshot.getValue();
                    if (rawData != null) {
                        Log.d(TAG, "Raw data: " + rawData.toString());
                        
                        // Try to manually create a BattleHistory object from the raw data
                        try {
                            String battleId = (String) rawData.get("battleId");
                            String opponentName = (String) rawData.get("opponentName");
                            String outcome = (String) rawData.get("outcome");
                            Object timestampObj = rawData.get("timestamp");
                            Date timestamp = null;
                            if (timestampObj instanceof Long) {
                                timestamp = new Date((Long) timestampObj);
                            } else if (timestampObj instanceof Map) {
                                // Handle Firebase server timestamp
                                Map<String, Object> timestampMap = (Map<String, Object>) timestampObj;
                                if (timestampMap.containsKey("time")) {
                                    timestamp = new Date((Long) timestampMap.get("time"));
                                }
                            }
                            if (timestamp == null) {
                                timestamp = new Date(); // Fallback
                            }
                            
                            String battleLog = (String) rawData.get("battleLog");
                            String playerTeam = (String) rawData.get("playerTeam");
                            String opponentTeam = (String) rawData.get("opponentTeam");
                            
                            BattleHistory manualBattleHistory = new BattleHistory(
                                    battleId,
                                    userId,
                                    opponentName,
                                    outcome,
                                    timestamp,
                                    battleLog,
                                    playerTeam,
                                    opponentTeam
                            );
                            
                            Log.d(TAG, "Manually created battle history: " + manualBattleHistory.getBattleId());
                            battleHistories.add(manualBattleHistory);
                        } catch (Exception e) {
                            Log.e(TAG, "Error creating battle history from raw data", e);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing battle history: " + e.getMessage(), e);
            }
        }
        
        Log.d(TAG, "Loaded " + battleHistories.size() + " battle histories");
        callback.onBattleHistoryLoaded(battleHistories);
    }
    
    /**
     * Get a specific battle by ID
     * @param battleId ID of the battle to retrieve
     * @param callback callback to handle the loaded battle
     */
    public void getBattleById(String battleId, BattleHistoryCallback callback) {
        mDatabase.child("battles").child(battleId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                BattleHistory battleHistory = dataSnapshot.getValue(BattleHistory.class);
                List<BattleHistory> result = new ArrayList<>();
                if (battleHistory != null) {
                    result.add(battleHistory);
                }
                callback.onBattleHistoryLoaded(result);
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                callback.onFailure(databaseError.toException());
            }
        });
    }
}
