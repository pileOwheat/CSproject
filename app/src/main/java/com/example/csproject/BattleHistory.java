package com.example.csproject;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Model class for storing battle history data
 */
@IgnoreExtraProperties
public class BattleHistory {
    @PropertyName("battleId")
    private String battleId;
    
    @PropertyName("userId")
    private String userId;
    
    @PropertyName("opponentName")
    private String opponentName;
    
    @PropertyName("outcome")
    private String outcome; // "win", "loss", or "tie"
    
    @PropertyName("timestamp")
    private Date timestamp;
    
    @PropertyName("battleLog")
    private String battleLog;
    
    @PropertyName("playerTeam")
    private String playerTeam;
    
    @PropertyName("opponentTeam")
    private String opponentTeam;

    // Empty constructor required for Firebase
    public BattleHistory() {
    }

    public BattleHistory(String battleId, String userId, String opponentName, String outcome, 
                         Date timestamp, String battleLog, String playerTeam, String opponentTeam) {
        this.battleId = battleId;
        this.userId = userId;
        this.opponentName = opponentName;
        this.outcome = outcome;
        this.timestamp = timestamp;
        this.battleLog = battleLog;
        this.playerTeam = playerTeam;
        this.opponentTeam = opponentTeam;
    }

    // Convert to Map for Firebase
    @Exclude
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("battleId", battleId);
        result.put("userId", userId);
        result.put("opponentName", opponentName);
        result.put("outcome", outcome);
        result.put("timestamp", timestamp);
        result.put("battleLog", battleLog);
        result.put("playerTeam", playerTeam);
        result.put("opponentTeam", opponentTeam);
        return result;
    }

    // Getters and setters
    @PropertyName("battleId")
    public String getBattleId() {
        return battleId;
    }

    @PropertyName("battleId")
    public void setBattleId(String battleId) {
        this.battleId = battleId;
    }

    @PropertyName("userId")
    public String getUserId() {
        return userId;
    }

    @PropertyName("userId")
    public void setUserId(String userId) {
        this.userId = userId;
    }

    @PropertyName("opponentName")
    public String getOpponentName() {
        return opponentName;
    }

    @PropertyName("opponentName")
    public void setOpponentName(String opponentName) {
        this.opponentName = opponentName;
    }

    @PropertyName("outcome")
    public String getOutcome() {
        return outcome;
    }

    @PropertyName("outcome")
    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    @PropertyName("timestamp")
    public Date getTimestamp() {
        return timestamp;
    }

    @PropertyName("timestamp")
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    @PropertyName("battleLog")
    public String getBattleLog() {
        return battleLog;
    }

    @PropertyName("battleLog")
    public void setBattleLog(String battleLog) {
        this.battleLog = battleLog;
    }

    @PropertyName("playerTeam")
    public String getPlayerTeam() {
        return playerTeam;
    }

    @PropertyName("playerTeam")
    public void setPlayerTeam(String playerTeam) {
        this.playerTeam = playerTeam;
    }

    @PropertyName("opponentTeam")
    public String getOpponentTeam() {
        return opponentTeam;
    }

    @PropertyName("opponentTeam")
    public void setOpponentTeam(String opponentTeam) {
        this.opponentTeam = opponentTeam;
    }
}
