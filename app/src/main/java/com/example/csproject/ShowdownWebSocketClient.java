package com.example.csproject;

import okhttp3.*;
import java.util.concurrent.TimeUnit;

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
        callback.onMessageReceived("📩 " + text);
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

