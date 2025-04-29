package com.example.csproject;

import android.util.Log;
import okhttp3.*;
import java.util.concurrent.TimeUnit;

public class ShowdownWebSocketClient extends WebSocketListener {

    public interface MessageCallback {
        void onMessageReceived(String message);
    }

    private WebSocket webSocket;
    private final OkHttpClient client;
    private final MessageCallback callback;
    private String battleRoomId = null; // Track the active battle room

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
            // If the message is a command and we're in a battle room, prepend the room ID
            if (message.startsWith("/")) {
                if (battleRoomId != null) {
                    webSocket.send(battleRoomId + "|" + message);
                } else {
                    callback.onMessageReceived("⚠️ Not currently in a battle room.");
                }
            } else {
                webSocket.send(message);
            }
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
        webSocket.send("|/utm null");
        webSocket.send("|/trn guest,0"); // Join as guest
        webSocket.send("|/search gen8randombattle");
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        String[] lines = text.split("\n");
        String currentRoom = null;

        for (String rawLine : lines) {
            if (rawLine.isEmpty()) continue;

            // Room ID is prefixed before the first '|', e.g., ">battle-gen8randombattle-12345"
            if (rawLine.startsWith(">")) {
                currentRoom = rawLine.substring(1).trim();
                continue;
            }

            // Avoid leading pipes and split on '|'
            String line = rawLine.trim();
            if (line.startsWith("|")) {
                line = line.replaceAll("^\\|+", "");
            }

            String[] parts = line.split("\\|");
            if (parts.length == 0) continue;

            String cmd = parts[0];

            switch (cmd) {
                case "init":
                    // Battle initialization
                    if (currentRoom != null && currentRoom.startsWith("battle-")) {
                        battleRoomId = currentRoom;
                        callback.onMessageReceived("⚔️ Joined battle: " + battleRoomId);
                    }
                    break;

                case "turn":
                    callback.onMessageReceived("\n🔁 Turn " + parts[1]);
                    break;

                case "move":
                    String attacker = parts[1].replaceAll("p\\d[a]?: ?", "");
                    String move = parts[2];
                    String target = (parts.length > 3) ? parts[3].replaceAll("p\\d[a]?: ?", "") : "";
                    callback.onMessageReceived("⚡ " + attacker + " used " + move + (target.isEmpty() ? "" : " on " + target) + "!");
                    break;

                case "-fail":
                    callback.onMessageReceived("🚫 " + parts[1] + "'s move failed!");
                    break;

                case "-immune":
                    callback.onMessageReceived("🛡️ " + parts[1] + " is immune!");
                    break;

                case "-miss":
                    callback.onMessageReceived("❌ " + parts[1] + "'s move missed!");
                    break;

                case "-crit":
                    callback.onMessageReceived("💥 Critical hit on " + parts[1] + "!");
                    break;

                case "-supereffective":
                    callback.onMessageReceived("🔥 It's super effective on " + parts[1] + "!");
                    break;

                case "-resisted":
                    callback.onMessageReceived("🛡️ The attack was not very effective on " + parts[1] + "!");
                    break;

                case "-damage":
                    callback.onMessageReceived("💥 " + parts[1] + " took damage! HP: " + (parts.length > 2 ? parts[2] : ""));
                    break;

                case "-heal":
                    callback.onMessageReceived("❤️ " + parts[1] + " healed. HP: " + (parts.length > 2 ? parts[2] : ""));
                    break;

                case "-status":
                    callback.onMessageReceived("🧪 " + parts[1] + " is now " + parts[2].toUpperCase() + "!");
                    break;

                case "-curestatus":
                    callback.onMessageReceived("🧼 " + parts[1] + " was cured of " + parts[2].toUpperCase() + "!");
                    break;

                case "-boost":
                case "-unboost":
                    String stat = parts[2];
                    String amt = parts[3];
                    String dir = cmd.equals("-boost") ? "rose" : "fell";
                    callback.onMessageReceived("📈 " + parts[1] + "'s " + stat + " " + dir + " by " + amt + "!");
                    break;

                case "switch":
                    String[] sw = parts[1].split(",");
                    callback.onMessageReceived("🔄 Switched to " + sw[0] + "!");
                    break;

                case "faint":
                    callback.onMessageReceived("💀 " + parts[1] + " fainted!");
                    break;

                case "win":
                    callback.onMessageReceived("\n🏆 " + parts[1] + " wins the battle!");
                    break;

                case "upkeep":
                    // Ignore or optionally show a turn transition
                    break;

                default:
                    // General fallback, avoid spammy output
                    if (!cmd.isEmpty()) {
                        callback.onMessageReceived("• " + String.join(" | ", parts));
                    }
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
