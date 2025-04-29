package com.example.csproject;

import android.util.Log;

import okhttp3.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

public class ShowdownWebSocketClient extends WebSocketListener {

    public interface MessageCallback {
        void onMessageReceived(String message);
    }

    private WebSocket webSocket;
    private final OkHttpClient client;
    private final MessageCallback callback;

    public ShowdownWebSocketClient(MessageCallback callback) {
        this.callback = callback;
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    public void connect() {
        Request request = new Request.Builder()
                .url("wss://sim3.psim.us/showdown/websocket")
                .build();

        client.newWebSocket(request, this);
    }

    public void send(String message) {
        if (webSocket != null) {
            webSocket.send(message);
        }
    }

    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "Closed by user");
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        this.webSocket = webSocket;
        callback.onMessageReceived("✅ Connected to Pokémon Showdown");
        webSocket.send("|/cmd roomlist");
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        String[] lines = text.split("\n");
        Log.i("WebSocket", "RAW: " + text);

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            //callback.onMessageReceived("🔎 RAW roomlist message:\n" + line); - for testing
            if (line.startsWith("|queryresponse|roomlist|")) {
                try {
                    int jsonStart = line.indexOf("|roomlist|") + "|roomlist|".length();
                    String jsonText = line.substring(jsonStart).trim();

                    JSONObject json = new JSONObject(jsonText);
                    JSONObject rooms = json.getJSONObject("rooms");

                    List<String> roomNames = new ArrayList<>();

                    Iterator<String> keys = rooms.keys();
                    while (keys.hasNext()) {
                        String roomName = keys.next();
                        if (roomName.startsWith("battle-")) {
                            roomNames.add(roomName);
                        }
                    }

                    if (!roomNames.isEmpty()) {
                        String selectedRoom = roomNames.get(0);
                        webSocket.send("|/join " + selectedRoom);
                        callback.onMessageReceived("🧭 Joining random room: " + selectedRoom);
                    } else {
                        callback.onMessageReceived("⚠ No active battle rooms found.");
                    }

                } catch (Exception e) {
                    callback.onMessageReceived("💥 Failed to parse room list: " + e.getMessage());
                }

                return;
            }



            // ✅ BATTLE EVENT PARSING
            if (line.contains("|turn|")) {
                String turnNum = line.split("\\|")[2];
                callback.onMessageReceived("\n🔁 Turn " + turnNum);
            } else if (line.contains("|move|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String attacker = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    String move = parts[3];
                    String target = parts.length > 4 ? parts[4].replace("p1a: ", "").replace("p2a: ", "") : "";
                    callback.onMessageReceived("⚡ " + attacker + " used " + move + (target.isEmpty() ? "" : " on " + target) + "!");
                }
            } else if (line.contains("|-fail|")) {
                String[] parts = line.split("\\|");
                String failed = (parts.length >= 3) ? parts[2].replace("p1a: ", "").replace("p2a: ", "") : "Something";
                callback.onMessageReceived("🚫 " + failed + "'s move failed!");
            } else if (line.contains("|-immune|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    String target = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    callback.onMessageReceived("🛡️ " + target + " is immune!");
                }
            } else if (line.contains("|-miss|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    String attacker = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    callback.onMessageReceived("❌ " + attacker + "'s move missed!");
                }
            } else if (line.contains("|-crit|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    String target = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    callback.onMessageReceived("💥 Critical hit on " + target + "!");
                }
            } else if (line.contains("|-supereffective|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    String target = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    callback.onMessageReceived("🔥 It's super effective on " + target + "!");
                }
            } else if (line.contains("|-resisted|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    String target = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    callback.onMessageReceived("🛡️ The attack was not very effective on " + target + "!");
                }
            } else if (line.contains("|-damage|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String target = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    String hpStatus = parts[3];
                    callback.onMessageReceived("💥 " + target + " took damage! HP: " + hpStatus);
                }
            } else if (line.contains("|-heal|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String target = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    String hpStatus = parts[3];
                    callback.onMessageReceived("❤️ " + target + " healed. HP: " + hpStatus);
                }
            } else if (line.contains("|-status|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String target = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    String status = parts[3];
                    callback.onMessageReceived("🧪 " + target + " is now " + status.toUpperCase() + "!");
                }
            } else if (line.contains("|-curestatus|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String target = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    String status = parts[3];
                    callback.onMessageReceived("🧼 " + target + " was cured of " + status.toUpperCase() + "!");
                }
            } else if (line.contains("|-boost|") || line.contains("|-unboost|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 5) {
                    String target = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    String stat = parts[3];
                    String amount = parts[4];
                    String direction = line.contains("-boost") ? "rose" : "fell";
                    callback.onMessageReceived("📈 " + target + "'s " + stat + " " + direction + " by " + amount + "!");
                }
            } else if (line.contains("|-item|") || line.contains("|-enditem|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String user = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    String item = parts[3];
                    String event = line.contains("|-enditem|") ? "consumed" : "revealed";
                    callback.onMessageReceived("🎁 " + user + " " + event + " item: " + item);
                }
            } else if (line.contains("|-ability|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String user = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    String ability = parts[3];
                    callback.onMessageReceived("🧠 " + user + "'s ability activated: " + ability + "!");
                }
            } else if (line.contains("|-weather|")) {
                String weather = line.split("\\|")[2];
                callback.onMessageReceived("☁ Weather: " + weather);
            } else if (line.contains("|-mega|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String user = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    String form = parts[3];
                    callback.onMessageReceived("✨ " + user + " Mega Evolved into " + form + "!");
                }
            } else if (line.contains("|-terastallize|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String user = parts[2].replace("p1a: ", "").replace("p2a: ", "");
                    String type = parts[3];
                    callback.onMessageReceived("💎 " + user + " Terastallized into " + type + " type!");
                }
            } else if (line.contains("|switch|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    String trainer = parts[2].startsWith("p1a:") ? "Player 1" : "Player 2";
                    String pokemon = parts[2].replace("p1a: ", "").replace("p2a: ", "").split(",")[0];
                    callback.onMessageReceived("🔄 " + trainer + " switched to " + pokemon + "!");
                }
            } else if (line.contains("|faint|")) {
                String fainted = line.split("\\|")[2].replace("p1a: ", "").replace("p2a: ", "");
                callback.onMessageReceived("💀 " + fainted + " fainted!");
            } else if (line.contains("|win|")) {
                String winner = line.split("\\|")[2];
                callback.onMessageReceived("\n🏆 " + winner + " wins the battle!");
            } else if (line.startsWith("|")) {
                // Fallback for any other Showdown protocol messages
                callback.onMessageReceived("• " + line.replace("|", " | "));
            }
        }
    }




    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        callback.onMessageReceived("❌ Closing: " + reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        callback.onMessageReceived("💥 Error: " + t.getMessage());
    }
}