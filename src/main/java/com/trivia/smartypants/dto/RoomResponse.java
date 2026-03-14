package com.trivia.smartypants.dto;

public class RoomResponse {
    private String id;
    private String ageGroup;
    private String topic;

    public RoomResponse() {}

    public RoomResponse(String id, String ageGroup, String topic) {
        this.id = id;
        this.ageGroup = ageGroup;
        this.topic = topic;
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
}

