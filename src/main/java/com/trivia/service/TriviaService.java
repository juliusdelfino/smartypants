package com.trivia.service;

import com.trivia.model.TriviaQuestion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;

@Service
public class TriviaService {

    private List<TriviaQuestion> triviaQuestions;

    @PostConstruct
    public void loadTriviaData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = new ClassPathResource("trivia-data.json").getInputStream();
        triviaQuestions = mapper.readValue(inputStream, new TypeReference<List<TriviaQuestion>>(){});
    }

    public List<TriviaQuestion> getTriviaQuestions() {
        return triviaQuestions;
    }
}