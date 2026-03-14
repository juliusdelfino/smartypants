package com.delfino.smartypants.model;

public class Player {
    private String id;
    private String name;
    private int score;
    private boolean answered;
    private String lastAnswer;
    private int sessionId;

    public Player() {
        this.id = generateId();
        this.score = 0;
        this.answered = false;
    }

    public Player(String name, int sessionId) {
        this();
        this.name = name;
        this.sessionId = sessionId;
    }

    private static String generateId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    // Increment score by 1 (backwards compatible)
    public void incrementScore() { incrementScore(1); }

    // Increment score by a weighted amount (e.g., faster answers award more points)
    public void incrementScore(int delta) { this.score += delta; }

    public boolean hasAnswered() { return answered; }
    public void setAnswered(boolean answered) { this.answered = answered; }
    public void resetAnswered() { this.answered = false; this.lastAnswer = null; }

    public String getLastAnswer() { return lastAnswer; }
    public void setLastAnswer(String lastAnswer) { this.lastAnswer = lastAnswer; }

    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }
}
