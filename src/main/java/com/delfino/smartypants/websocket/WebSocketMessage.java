package com.delfino.smartypants.websocket;

public class WebSocketMessage {
    private String type;
    private String roomCode;
    private String playerId;
    private String playerName;
    private Object data;

    // Message types
    public static final String JOIN_ROOM = "JOIN_ROOM";
    public static final String PLAYER_JOINED = "PLAYER_JOINED";
    public static final String GAME_STARTED = "GAME_STARTED";
    public static final String NEW_QUESTION = "NEW_QUESTION";
    public static final String SUBMIT_ANSWER = "SUBMIT_ANSWER";
    public static final String PLAYER_ANSWERED = "PLAYER_ANSWERED";
    public static final String SHOW_ANSWER = "SHOW_ANSWER";
    public static final String NEXT_QUESTION = "NEXT_QUESTION";
    public static final String GAME_OVER = "GAME_OVER";
    public static final String TIMER_UPDATE = "TIMER_UPDATE";
    public static final String PLAYER_LEFT = "PLAYER_LEFT";
    public static final String ERROR = "ERROR";

    public WebSocketMessage() {}

    public WebSocketMessage(String type, String roomCode, String playerId) {
        this.type = type;
        this.roomCode = roomCode;
        this.playerId = playerId;
    }

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public static WebSocketMessage joinRoom(String roomCode, String playerName) {
        WebSocketMessage msg = new WebSocketMessage(JOIN_ROOM, roomCode, null);
        msg.setPlayerName(playerName);
        return msg;
    }

    public static WebSocketMessage playerJoined(String playerId, String playerName, int playerCount) {
        WebSocketMessage msg = new WebSocketMessage(PLAYER_JOINED, null, playerId);
        msg.setPlayerName(playerName);
        msg.setData(playerCount);
        return msg;
    }

    public static WebSocketMessage error(String message) {
        WebSocketMessage msg = new WebSocketMessage(ERROR, null, null);
        msg.setData(message);
        return msg;
    }
}
