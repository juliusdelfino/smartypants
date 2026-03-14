package com.trivia.smartypants.ws;

import com.trivia.smartypants.model.Player;
import com.trivia.smartypants.model.RoomState;
import com.trivia.smartypants.service.RoomService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WsController {
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    // Default question time used if client doesn't provide timeLeft
    private static final int DEFAULT_QUESTION_TIME = 30;

    public WsController(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/rooms/{roomId}/join")
    public void join(@DestinationVariable String roomId, @Payload Map<String, String> payload) {
        String playerId = payload.getOrDefault("playerId", java.util.UUID.randomUUID().toString());
        String name = payload.getOrDefault("name", "Player");
        Player p = new Player(playerId, name);
        roomService.addPlayerToRoom(roomId, p);
        // broadcast updated players list
        RoomState state = roomService.getRoomState(roomId).orElse(null);
        if (state != null) {
            List<Player> top5 = state.topN(5);
            Map<String, Object> out = new HashMap<>();
            out.put("type", "players");
            out.put("players", state.allSorted());
            out.put("top5", top5);
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId, out);
        }
    }

    @MessageMapping("/rooms/{roomId}/answer")
    public void answer(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        if (payload == null) return;
        String playerId = (String) payload.get("playerId");
        Boolean correct = (Boolean) payload.get("correct");
        Integer timeLeft = (Integer) payload.get("timeLeft");
        if (playerId == null || correct == null) return;

        // If correct, compute points based on speed. Base 100 + 2 * timeLeft (so faster answers get more)
        if (correct) {
            int tl = timeLeft == null ? 0 : timeLeft;
            int delta = 100 + (2 * tl);
            roomService.addPlayerPoints(roomId, playerId, delta);
        }

        RoomState state = roomService.getRoomState(roomId).orElse(null);
        if (state != null) {
            Map<String, Object> out = new HashMap<>();
            out.put("type", "top5");
            out.put("top5", state.topN(5));
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId, out);
        }
    }

    // Moderator advance mapping: first broadcast snapshot of full scores to everyone, then forward advance message
    @MessageMapping("/rooms/{roomId}/advance")
    public void advance(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        RoomState state = roomService.getRoomState(roomId).orElse(null);
        if (state != null) {
            Map<String, Object> snap = new HashMap<>();
            snap.put("type", "scores_snapshot");
            snap.put("scores", state.allSorted());
            // send snapshot
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId, snap);
        }
        // Forward the advance payload after a short delay so clients have time to render the scores.
        if (payload != null) {
            final Map<String, Object> forward = new HashMap<>(payload);
            new Thread(() -> {
                try {
                    Thread.sleep(1500); // pause 1.5s to allow clients to display snapshot
                } catch (InterruptedException ignored) {}
                messagingTemplate.convertAndSend("/topic/rooms/" + roomId, forward);
            }).start();
        }
    }

    // Generic notify mapping: relay arbitrary payloads (start/advance/etc.) to all subscribers for the room
    @MessageMapping("/rooms/{roomId}/notify")
    public void notifyRoom(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        if (payload == null) return;
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId, payload);
    }

    // Helper used by controllers to broadcast start event (with questions)
    public void sendStart(String roomId, List<?> questions) {
        Map<String, Object> out = new HashMap<>();
        out.put("type", "start");
        out.put("questions", questions);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId, out);
    }

    // When game ends, send full scoreboard
    public void sendFinalScores(String roomId) {
        RoomState state = roomService.getRoomState(roomId).orElse(null);
        if (state != null) {
            Map<String,Object> out = new HashMap<>();
            out.put("type", "final");
            out.put("scores", state.allSorted());
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId, out);
        }
    }
}
