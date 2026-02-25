package com.trivia.service;

import com.trivia.model.TriviaQuestion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class TriviaService {

    @Autowired
    private OllamaService ollamaService;

    private List<TriviaQuestion> triviaQuestions;

    public List<TriviaQuestion> getTriviaQuestions(String ageGroup, String topic) {
        try {
            String jsonResponse = ollamaService.generateTrivia(ageGroup, topic);
            jsonResponse = cleanResponse(jsonResponse);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(jsonResponse, new TypeReference<List<TriviaQuestion>>(){});
        } catch (Exception e) {
            // Fallback to sample data if API fails
            return getDefaultQuestions();
        }
    }

    private String cleanResponse(String jsonResponse) {
        return jsonResponse.replace("```json", "").replace("```", "");
    }

    private List<TriviaQuestion> getDefaultQuestions() {
        // Return sample data as fallback
        return List.of(
            new TriviaQuestion("What is the capital of France?", List.of("London", "Berlin", "Paris", "Madrid"), 2),
            new TriviaQuestion("Which planet is known as the Red Planet?", List.of("Venus", "Mars", "Jupiter", "Saturn"), 1),
            new TriviaQuestion("What is the largest mammal in the world?", List.of("Elephant", "Blue Whale", "Giraffe", "Hippopotamus"), 1),
            new TriviaQuestion("Which element has the chemical symbol 'O'?", List.of("Gold", "Oxygen", "Osmium", "Oganesson"), 1),
            new TriviaQuestion("Who painted the Mona Lisa?", List.of("Vincent van Gogh", "Pablo Picasso", "Leonardo da Vinci", "Michelangelo"), 2)
        );
    }
}