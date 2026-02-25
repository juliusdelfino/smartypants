package com.trivia.controller;

import com.trivia.model.TriviaQuestion;
import com.trivia.service.TriviaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trivia")
public class TriviaController {

    @Autowired
    private TriviaService triviaService;

    @GetMapping("/questions")
    public List<TriviaQuestion> getQuestions() {
        return triviaService.getTriviaQuestions();
    }
}