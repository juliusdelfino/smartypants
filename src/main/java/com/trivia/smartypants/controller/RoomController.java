package com.trivia.smartypants.controller;

import com.trivia.smartypants.dto.CreateRoomRequest;
import com.trivia.smartypants.dto.RoomResponse;
import com.trivia.smartypants.model.Question;
import com.trivia.smartypants.model.Room;
import com.trivia.smartypants.service.RoomService;
import com.trivia.smartypants.ws.WsController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService roomService;
    private final WsController wsController;

    public RoomController(RoomService roomService, WsController wsController) {
        this.roomService = roomService;
        this.wsController = wsController;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@RequestBody CreateRoomRequest req) {
        Room room = roomService.createRoom(req.getAgeGroup(), req.getTopic());
        return ResponseEntity.ok(new RoomResponse(room.getId(), room.getAgeGroup(), room.getTopic()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable String id) {
        return roomService.getRoom(id)
                .map(r -> ResponseEntity.ok(new RoomResponse(r.getId(), r.getAgeGroup(), r.getTopic())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<List<Question>> start(@PathVariable String id) {
        try {
            List<Question> questions = roomService.startRoom(id);
            // Broadcast start to any connected clients via WS
            wsController.sendStart(id, questions);
            return ResponseEntity.ok(questions);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<Void> end(@PathVariable String id) {
        // broadcast final scores via WebSocket
        wsController.sendFinalScores(id);
        return ResponseEntity.ok().build();
    }
}
