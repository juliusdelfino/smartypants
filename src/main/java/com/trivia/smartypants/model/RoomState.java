package com.trivia.smartypants.model;

import java.util.*;
import java.util.stream.Collectors;

public class RoomState {
    private final String roomId;
    private final Map<String, Player> players = new LinkedHashMap<>();

    public RoomState(String roomId) {
        this.roomId = roomId;
    }

    public synchronized void addPlayer(Player p) {
        players.put(p.getId(), p);
    }

    public synchronized void removePlayer(String id) {
        players.remove(id);
    }

    public synchronized Collection<Player> getPlayers() {
        return players.values();
    }

    public synchronized Optional<Player> getPlayer(String id) {
        return Optional.ofNullable(players.get(id));
    }

    public synchronized void updatePlayerScore(String id, int score) {
        Player p = players.get(id);
        if (p != null) p.setScore(score);
    }

    public synchronized List<Player> topN(int n) {
        return players.values().stream()
                .sorted(Comparator.comparingInt(Player::getScore).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    public synchronized List<Player> allSorted() {
        return players.values().stream()
                .sorted(Comparator.comparingInt(Player::getScore).reversed())
                .collect(Collectors.toList());
    }
}
