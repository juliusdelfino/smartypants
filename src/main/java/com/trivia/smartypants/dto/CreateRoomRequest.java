package com.trivia.smartypants.dto;

public class CreateRoomRequest {
    private String ageGroup;
    private String topic;

    public CreateRoomRequest() {}

    public CreateRoomRequest(String ageGroup, String topic) {
        this.ageGroup = ageGroup;
        this.topic = topic;
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
