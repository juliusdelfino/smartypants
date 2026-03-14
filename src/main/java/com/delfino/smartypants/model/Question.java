package com.delfino.smartypants.model;

import java.util.List;

public class Question {
    private String question;
    private List<String> options;
    private int correctAnswerIndex;

    // Legacy fields for backward compatibility
    private String correctAnswer;
    private List<String> wrongAnswers;

    public Question() {}

    // Legacy constructor
    public Question(String question, String correctAnswer, List<String> wrongAnswers) {
        this.question = question;
        this.correctAnswer = correctAnswer;
        this.wrongAnswers = wrongAnswers;
        // Build options list with correct answer first
        this.options = new java.util.ArrayList<>();
        this.options.add(correctAnswer);
        this.options.addAll(wrongAnswers);
        this.correctAnswerIndex = 0;
    }

    // New constructor for Ollama format
    public Question(String question, List<String> options, int correctAnswerIndex) {
        this.question = question;
        this.options = options;
        this.correctAnswerIndex = correctAnswerIndex;
        this.correctAnswer = options.get(correctAnswerIndex);
    }

    // Getters and Setters
    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public int getCorrectAnswerIndex() {
        return correctAnswerIndex;
    }

    public void setCorrectAnswerIndex(int correctAnswerIndex) {
        this.correctAnswerIndex = correctAnswerIndex;
    }

    // Legacy compatibility methods
    public String getCorrectAnswer() {
        if (correctAnswer != null) {
            return correctAnswer;
        }
        if (options != null && correctAnswerIndex >= 0 && correctAnswerIndex < options.size()) {
            return options.get(correctAnswerIndex);
        }
        return null;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public List<String> getWrongAnswers() {
        if (wrongAnswers != null) {
            return wrongAnswers;
        }
        // Derive wrong answers from options
        if (options != null) {
            List<String> wrong = new java.util.ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                if (i != correctAnswerIndex) {
                    wrong.add(options.get(i));
                }
            }
            return wrong;
        }
        return null;
    }

    public void setWrongAnswers(List<String> wrongAnswers) {
        this.wrongAnswers = wrongAnswers;
    }
}
