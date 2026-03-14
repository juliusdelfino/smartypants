package com.delfino.smartypants.controller;

import com.delfino.smartypants.model.CreateRoomRequest;
import com.delfino.smartypants.model.Player;
import com.delfino.smartypants.model.Question;
import com.delfino.smartypants.model.Room;
import com.delfino.smartypants.service.OllamaQuestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class GameController {

    private final OllamaQuestionService questionService;
    private final Map<String, List<Question>> roomQuestions = new HashMap<>();

    public GameController(OllamaQuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/rooms")
    public ResponseEntity<Room> createRoom(@RequestBody CreateRoomRequest request) {
        Room room = Room.createRoom(request.getAgeGroup(), request.getTopic());
        // Store room, questions will be generated when game starts via WebSocket
        roomQuestions.put(room.getRoomCode(), new ArrayList<>());
        return ResponseEntity.ok(room);
    }

    @PostMapping("/rooms/{roomCode}/join")
    public ResponseEntity<? extends Object> joinRoom(@PathVariable String roomCode) {
        Room room = Room.getRoom(roomCode);
        if (room == null) {
            return ResponseEntity.badRequest().body("Room not found");
        }
        return ResponseEntity.ok(room);
    }

    @GetMapping("/rooms/{roomCode}/defaults")
    public ResponseEntity<Map<String, String[]>> getDefaults(@PathVariable String roomCode) {
        Map<String, String[]> defaults = new HashMap<>();
        defaults.put("ageGroups", Room.getDefaultAgeGroups());
        defaults.put("topics", Room.getDefaultTopics());
        return ResponseEntity.ok(defaults);
    }

    @GetMapping("/rooms/{roomCode}/players")
    public ResponseEntity<Set<Player>> getRoomPlayers(@PathVariable String roomCode) {
        Room room = Room.getRoom(roomCode);
        if (room == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(room.getPlayers());
    }

    @GetMapping("/rooms/{roomCode}/status")
    public ResponseEntity<Map<String, Object>> getRoomStatus(@PathVariable String roomCode) {
        Room room = Room.getRoom(roomCode);
        if (room == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Room not found"));
        }
        
        Map<String, Object> status = new HashMap<>();
        status.put("roomCode", room.getRoomCode());
        status.put("gameState", room.getGameState());
        status.put("currentQuestionIndex", room.getCurrentQuestionIndex());
        status.put("playerCount", room.getPlayerCount());
        status.put("topic", room.getTopic());
        status.put("ageGroup", room.getAgeGroup());
        return ResponseEntity.ok(status);
    }
}
