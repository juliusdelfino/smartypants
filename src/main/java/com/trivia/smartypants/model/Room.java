package com.trivia.smartypants.model;

import java.util.ArrayList;
import java.util.List;

public class Room {
    private String id; // 6-digit
    private String ageGroup;
    private String topic;
    private String difficulty;
    private List<Question> questions = new ArrayList<>();

    public Room() {}

    public Room(String id, String ageGroup, String topic) {
        this.id = id;
        this.ageGroup = ageGroup;
        this.topic = topic;
    }

    public Room(String id, String ageGroup, String topic, String difficulty) {
        this.id = id;
        this.ageGroup = ageGroup;
        this.topic = topic;
        this.difficulty = difficulty;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Question> questions) {
        this.questions = questions;
    }
}
