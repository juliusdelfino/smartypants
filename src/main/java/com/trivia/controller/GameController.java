package com.trivia.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GameController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/game")
    public String game(Model model,
                       @RequestParam(required = false) String roomCode,
                       @RequestParam(required = false) String ageGroup,
                       @RequestParam(required = false) String topic) {
        model.addAttribute("roomCode", roomCode);
        model.addAttribute("ageGroup", ageGroup);
        model.addAttribute("topic", topic);
        return "game";
    }
}