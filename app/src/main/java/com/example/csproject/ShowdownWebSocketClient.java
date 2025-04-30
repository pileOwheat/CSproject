package com.example.csproject;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShowdownWebSocketClient extends WebSocketListener {
    public interface MessageCallback {
        void onMessageReceived(String msg);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client;
    private final MessageCallback callback;
    private WebSocket webSocket;

    private String battleRoomId;
    private JSONObject lastRequestJson;
    private int mySlot = -1;

    public ShowdownWebSocketClient(MessageCallback callback) {
        this.callback = callback;
        this.client = new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build();
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

    @Override
    public void onOpen(WebSocket ws, Response resp) {
        this.webSocket = ws;
        callback.onMessageReceived("✅ Connected to Showdown");

        String guest = "guest" + (int) (Math.random() * 10000);
        ws.send("|/trn " + guest + ",0");

        ws.send("|/search randombattle");
        handler.postDelayed(this::retrySearch, 10000);
    }

    @Override
    public void onMessage(WebSocket ws, String text) {
        String[] lines = text.split("\n");
        String currentRoom = null;

        for (String raw : lines) {
            if (raw.startsWith(">")) {
                currentRoom = raw.substring(1).trim();
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("|")) line = line.substring(1);

            String[] p = line.split("\\|", -1);
            if (p.length == 0) continue;

            switch (p[0]) {
                case "updateSearch":
                    Matcher m = Pattern.compile("\"(battle-[^\"]+)\"").matcher(line);
                    if (m.find()) {
                        battleRoomId = m.group(1);
                        callback.onMessageReceived("⚔️ Matched: " + battleRoomId);
                        ws.send("|/join " + battleRoomId);
                        handler.removeCallbacksAndMessages(null);
                    }
                    break;

                case "init":
                    if (currentRoom != null && currentRoom.startsWith("battle-")) {
                        battleRoomId = currentRoom;
                        callback.onMessageReceived("⚔️ Joined: " + battleRoomId);
                        handler.removeCallbacksAndMessages(null);
                    }
                    break;

                case "request":
                    try {
                        lastRequestJson = new JSONObject(line.substring("request|".length()));
                        Log.d("ShowdownClient", "Request JSON: " + lastRequestJson.toString());

                        // Check for "side" and "foeSide" in the JSON
                        if (lastRequestJson.has("side")) {
                            JSONObject side = lastRequestJson.getJSONObject("side");
                            Log.d("ShowdownClient", "Side info: " + side.toString());
                        }

                        if (lastRequestJson.has("foeSide")) {
                            JSONObject foeSide = lastRequestJson.getJSONObject("foeSide");
                            Log.d("ShowdownClient", "Foe side info: " + foeSide.toString());
                        } else {
                            Log.d("ShowdownClient", "Foe side is still missing!");
                        }

                        // Set mySlot based on side ID
                        String sideId = lastRequestJson.getJSONObject("side").getString("id");
                        mySlot = sideId.equals("p1") ? 1 : 2;
                    } catch (JSONException e) {
                        Log.e("ShowdownClient", "request JSON error", e);
                    }
                    break;

                case "turn":
                    callback.onMessageReceived("\n🔁 Turn " + p[1]);
                    break;

                case "move": {
                    String user = p[1].replaceAll("p\\d[a]?: ?", "");
                    String mv = p[2];
                    String tgt = p.length > 3 ? p[3].replaceAll("p\\d[a]?: ?", "") : "";
                    callback.onMessageReceived("⚡ " + user + " used " + mv + (tgt.isEmpty() ? "" : " on " + tgt) + "!");
                    break;
                }

                case "damage":
                    callback.onMessageReceived("💢 " + p[1] + " took damage!");
                    break;

                case "heal":
                    callback.onMessageReceived("💚 " + p[1] + " healed!");
                    break;

                case "faint":
                    callback.onMessageReceived("💀 " + p[1] + " fainted!");
                    break;

                case "switch": {
                    String[] parts = p[1].split(": ");
                    if (parts.length < 2) {
                        Log.w("BATTLE_PARSE", "Invalid switch message: " + p[1]);
                        break;
                    }
                    String slotTag = parts[0];
                    String monName = parts[1];
                    int slot = slotTag.startsWith("p1") ? 1 : 2;
                    String msg = (slot == mySlot)
                            ? "👉 You switched in " + monName + "!"
                            : "👈 Opponent switched in " + monName + "!";
                    Log.d("BATTLE_PARSE", "Parsed switch: mon=" + monName + ", slot=" + slot + ", mySlot=" + mySlot);
                    callback.onMessageReceived(msg);
                    break;
                }

                case "win":
                    callback.onMessageReceived("🏆 Winner: " + p[1]);
                    break;

                case "tie":
                    callback.onMessageReceived("🤝 It's a tie!");
                    break;

                case "error":
                    callback.onMessageReceived("⚠️ Error: " + p[1]);
                    break;

                default:
                    // Unhandled message type
                    break;
            }
        }
    }

    private void retrySearch() {
        if (battleRoomId == null && webSocket != null) {
            callback.onMessageReceived("🔄 Retrying randombattle search...");
            webSocket.send("|/search randombattle");
            handler.postDelayed(this::retrySearch, 10000);
        }
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
