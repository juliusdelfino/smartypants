package com.trivia.smartypants.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trivia.smartypants.model.Player;
import com.trivia.smartypants.model.Question;
import com.trivia.smartypants.model.Room;
import com.trivia.smartypants.model.RoomState;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Service
public class RoomService {
    private final Map<String, Room> rooms = new HashMap<>();
    private final Map<String, RoomState> roomStates = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper mapper = new ObjectMapper();

    public Room createRoom(String ageGroup, String topic) {
        String id = generateRoomId();
        Room room = new Room(id, ageGroup, topic);
        rooms.put(id, room);
        roomStates.put(id, new RoomState(id));
        return room;
    }

    public Optional<Room> getRoom(String id) {
        return Optional.ofNullable(rooms.get(id));
    }

    public Optional<RoomState> getRoomState(String id) { return Optional.ofNullable(roomStates.get(id)); }

    private String generateRoomId() {
        int num = 100000 + random.nextInt(900000);
        String id = String.valueOf(num);
        // ensure uniqueness
        while (rooms.containsKey(id)) {
            num = 100000 + random.nextInt(900000);
            id = String.valueOf(num);
        }
        return id;
    }

    public List<Question> loadSampleQuestions() throws IOException {
        InputStream is = getClass().getResourceAsStream("/static/data/sample_questions.json");
        if (is == null) return Collections.emptyList();
        return mapper.readValue(is, new TypeReference<List<Question>>(){});
    }

    public List<Question> generateQuestionsForRoom(Room room) throws IOException {
        // Read the instruction template
        InputStream mdStream = getClass().getResourceAsStream("/trivia.md");
        String template = "";
        if (mdStream != null) {
            template = new String(mdStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Replace placeholders
        String prompt = template.replace("[age-group]", room.getAgeGroup() == null ? "All ages" : room.getAgeGroup())
                .replace("[topic]", room.getTopic() == null ? "General Knowledge" : room.getTopic());

        // Prepare request to local generator
        URL url = new URL("http://localhost:11434/api/generate");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // Request body: model + prompt
        Map<String, Object> body = new HashMap<>();
        body.put("model", "gemma3");
        body.put("prompt", prompt);
        body.put("stream", false);
        String jsonBody = mapper.writeValueAsString(body);
        conn.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));

        int status = conn.getResponseCode();
        if (status >= 200 && status < 300) {
            InputStream resp = conn.getInputStream();
            String jsonResponse = cleanResponse(new String(resp.readAllBytes(), StandardCharsets.UTF_8));
            JsonNode root = mapper.readTree(jsonResponse);
            // Expect the API to return generated text in a field named "text" or directly JSON array
            // Try several possibilities:
            if (root.isArray()) {
                // Directly an array of objects
                return mapper.convertValue(root, new TypeReference<List<Question>>(){});
            } else if (root.has("response")) {
                String text = root.get("response").asText();
                // parse the text as JSON array
                return mapper.readValue(text, new TypeReference<List<Question>>(){});
            } else {
                // Fallback: try to find first array node inside response
                JsonNode arr = null;
                Iterator<JsonNode> it = root.elements();
                while (it.hasNext()) {
                    JsonNode n = it.next();
                    if (n.isArray()) { arr = n; break; }
                }
                if (arr != null) return mapper.convertValue(arr, new TypeReference<List<Question>>(){});
            }
        }

        // On any failure, fall back to bundled sample questions
        return loadSampleQuestions();
    }

    private String cleanResponse(String s) {
        return s.replace("```json", "")
                .replace("```", "")
                .trim();
    }

    public List<Question> startRoom(String id) throws IOException {
        Room room = rooms.get(id);
        if (room == null) throw new IllegalArgumentException("Room not found");
        try {
            List<Question> questions = loadSampleQuestions(); // generateQuestionsForRoom(room);
            room.setQuestions(questions);
            return questions;
        } catch (Exception e) {
            e.printStackTrace();
            return loadSampleQuestions();
        }
    }

    public void addPlayerToRoom(String roomId, Player player) {
        RoomState state = roomStates.get(roomId);
        if (state != null) {
            state.addPlayer(player);
        }
    }

    public void removePlayerFromRoom(String roomId, String playerId) {
        RoomState state = roomStates.get(roomId);
        if (state != null) {
            state.removePlayer(playerId);
        }
    }

    public void updatePlayerScore(String roomId, String playerId, int score) {
        RoomState state = roomStates.get(roomId);
        if (state != null) {
            state.updatePlayerScore(playerId, score);
        }
    }

    // New helper: increment player's score by a delta (used to credit points based on quickness)
    public void addPlayerPoints(String roomId, String playerId, int delta) {
        RoomState state = roomStates.get(roomId);
        if (state != null) {
            state.getPlayer(playerId).ifPresent(p -> p.addScore(delta));
        }
    }
}
