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

                case "-damage":
                    callback.onMessageReceived("💥 " + p[1] + " took damage! HP: " + p[2]);
                    break;

                case "-heal":
                    callback.onMessageReceived("❤️ " + p[1] + " healed. HP: " + p[2]);
                    break;

                case "-status":
                    callback.onMessageReceived("🧪 " + p[1] + " is now " + p[2].toUpperCase() + "!");
                    break;

                case "-curestatus":
                    callback.onMessageReceived("🧼 " + p[1] + " cured of " + p[2].toUpperCase() + "!");
                    break;

                case "-boost":
                case "-unboost": {
                    String stat = p[2], amt = p[3];
                    String dir = p[0].equals("-boost") ? "rose" : "fell";
                    callback.onMessageReceived("📈 " + p[1] + "'s " + stat + " " + dir + " by " + amt + "!");
                    break;
                }

                case "switch": {
                    String slotAndName = p[1].split(",")[0];
                    String mon = slotAndName.replaceAll("p\\d[a]?: ?", "").trim();
                    int slot = slotAndName.startsWith("p1") ? 1 : 2;
                    String msg = (slot == mySlot)
                            ? "👉 You switched in " + mon + "!"
                            : "👈 Opponent switched in " + mon + "!";
                    callback.onMessageReceived(msg);
                    break;
                }

                case "faint":
                    callback.onMessageReceived("💀 " + p[1] + " fainted!");
                    break;

                case "win":
                    callback.onMessageReceived("\n🏆 " + p[1] + " wins!");
                    break;

                case "-fail":
                    callback.onMessageReceived("🚫 " + p[1] + " failed!");
                    break;

                case "-miss":
                    callback.onMessageReceived("❌ " + p[1] + " missed!");
                    break;

                case "error":
                    callback.onMessageReceived("⚠️ Error: " + (p.length > 1 ? p[1] : "unknown"));
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
