package com.delfino.smartypants.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class Room {
    private String roomCode;
    private String ageGroup;
    private String topic;
    private GameState gameState;
    private int currentQuestionIndex;
    private Set<Player> players;
    private String hostPlayerId;
    private long questionStartTime;
    private static final long QUESTION_TIMEOUT_MS = 30000; // 30 seconds

    public enum GameState {
        WAITING,
        PLAYING,
        QUESTION_ACTIVE,
        SHOWING_ANSWER,
        FINISHED
    }

    private static final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private static final Random random = new Random();
    private static final String[] DEFAULT_AGE_GROUPS = {"Kids (6-12)", "Teens (13-17)", "Adults (18+)", "All Ages"};
    private static final String[] DEFAULT_TOPICS = {"General Knowledge", "Science", "History", "Sports", "Entertainment", "Geography"};

    public Room() {
        this.roomCode = generateRoomCode();
        this.gameState = GameState.WAITING;
        this.currentQuestionIndex = 0;
        this.players = new CopyOnWriteArraySet<>();
    }

    public Room(String ageGroup, String topic) {
        this();
        this.ageGroup = ageGroup;
        this.topic = topic;
    }

    private static String generateRoomCode() {
        String code;
        do {
            code = String.format("%06d", random.nextInt(1000000));
        } while (rooms.containsKey(code));
        return code;
    }

    public static Room createRoom(String ageGroup, String topic) {
        Room room = new Room(ageGroup, topic);
        rooms.put(room.getRoomCode(), room);
        return room;
    }

    public static Room getRoom(String roomCode) {
        return rooms.get(roomCode);
    }

    public static boolean roomExists(String roomCode) {
        return rooms.containsKey(roomCode);
    }

    public static String[] getDefaultAgeGroups() {
        return DEFAULT_AGE_GROUPS;
    }

    public static String[] getDefaultTopics() {
        return DEFAULT_TOPICS;
    }

    // Player management
    public Player addPlayer(String name) {
        Player player = new Player(name, players.size() + 1);
        // First player is host
        if (players.isEmpty()) {
            hostPlayerId = player.getId();
        }
        players.add(player);
        return player;
    }

    public Player getPlayer(String playerId) {
        return players.stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    public void removePlayer(String playerId) {
        players.removeIf(p -> p.getId().equals(playerId));
    }

    public Set<Player> getPlayers() {
        return players;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public boolean hasPlayerAnswered(String playerId) {
        Player player = getPlayer(playerId);
        return player != null && player.hasAnswered();
    }

    public int getAnsweredCount() {
        return (int) players.stream().filter(Player::hasAnswered).count();
    }

    public boolean allPlayersAnswered() {
        return getAnsweredCount() == players.size();
    }

    public void resetAllPlayerAnswers() {
        players.forEach(Player::resetAnswered);
    }

    // Question timeout
    public void startQuestionTimer() {
        this.questionStartTime = System.currentTimeMillis();
    }

    public long getQuestionTimeRemaining() {
        long elapsed = System.currentTimeMillis() - questionStartTime;
        long remaining = QUESTION_TIMEOUT_MS - elapsed;
        return Math.max(0, remaining);
    }

    public boolean isQuestionTimedOut() {
        return getQuestionTimeRemaining() <= 0;
    }

    public int getQuestionTimeoutSeconds() {
        return (int) (QUESTION_TIMEOUT_MS / 1000);
    }

    // Host check
    public boolean isHost(String playerId) {
        return playerId != null && playerId.equals(hostPlayerId);
    }

    // Getters and Setters
    public String getRoomCode() {
        return roomCode;
    }

    public String getAgeGroup() {
        return ageGroup;
    }

    public void setAgeGroup(String ageGroup) {
        this.ageGroup = ageGroup;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public void setCurrentQuestionIndex(int currentQuestionIndex) {
        this.currentQuestionIndex = currentQuestionIndex;
    }

    public void nextQuestion() {
        this.currentQuestionIndex++;
    }

    public String getHostPlayerId() {
        return hostPlayerId;
    }
}
