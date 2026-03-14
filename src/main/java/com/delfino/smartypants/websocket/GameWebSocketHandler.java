package com.delfino.smartypants.websocket;

import com.delfino.smartypants.model.Player;
import com.delfino.smartypants.model.Question;
import com.delfino.smartypants.model.Room;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.delfino.smartypants.service.OllamaQuestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GameWebSocketHandler.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OllamaQuestionService questionService;

    // Session mappings
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionPlayerMap = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRoomMap = new ConcurrentHashMap<>();
    private final Map<String, List<Question>> roomQuestions = new ConcurrentHashMap<>();
    // Store the shuffled options for the currently active question per room so late joiners see the same order
    private final Map<String, List<String>> roomShuffledOptions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> roomTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    public GameWebSocketHandler(OllamaQuestionService questionService) {
        this.questionService = questionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LOG.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebSocketMessage wsMessage = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);
        
        switch (wsMessage.getType()) {
            case WebSocketMessage.JOIN_ROOM:
                handleJoinRoom(session, wsMessage);
                break;
            case "PLAYER_LEAVE":
                handlePlayerLeave(session, wsMessage);
                break;
            case WebSocketMessage.SUBMIT_ANSWER:
                handleSubmitAnswer(session, wsMessage);
                break;
            case WebSocketMessage.NEXT_QUESTION:
                handleNextQuestion(session, wsMessage);
                break;
            case WebSocketMessage.GAME_STARTED:
                handleStartGame(session, wsMessage);
                break;
            default:
                sendError(session, "Unknown message type: " + wsMessage.getType());
        }
    }

    private void handleJoinRoom(WebSocketSession session, WebSocketMessage message) {
        String roomCode = message.getRoomCode();
        String playerName = message.getPlayerName();
        
        if (roomCode == null || playerName == null || playerName.trim().isEmpty()) {
            sendError(session, "Room code and player name are required");
            return;
        }

        Room room = Room.getRoom(roomCode);
        if (room == null) {
            sendError(session, "Room not found: " + roomCode);
            return;
        }

        // Add player to room
        Player player = room.addPlayer(playerName.trim());
        
        // Register session
        sessions.put(session.getId(), session);
        sessionPlayerMap.put(session.getId(), player.getId());
        sessionRoomMap.put(session.getId(), roomCode);

        // Send player their ID and current players
        WebSocketMessage response = new WebSocketMessage();
        response.setType(WebSocketMessage.PLAYER_JOINED);
        response.setPlayerId(player.getId());
        response.setPlayerName(player.getName());
        response.setData(Map.of(
            "playerId", player.getId(),
            "playerName", player.getName(),
            "players", getPlayerList(room),
            "isHost", room.isHost(player.getId()),
            "roomCode", roomCode,
                "ageGroup", room.getAgeGroup(),
        "topic", room.getTopic()
        ));
        sendMessage(session, response);

        // Notify other players
        broadcastToRoom(roomCode, session.getId(), createPlayerJoinedMessage(player, room));

        // If the game is already in progress, send the current state to the new player so they can join mid-session
        if (room.getGameState() == Room.GameState.QUESTION_ACTIVE) {
            // Send the active question to the joining session using the stored shuffled options if available
            List<Question> questions = roomQuestions.get(roomCode);
            if (questions != null) {
                int qIndex = room.getCurrentQuestionIndex();
                Question currentQuestion = questions.get(qIndex);

                List<String> options = roomShuffledOptions.computeIfAbsent(roomCode, k -> {
                    List<String> opts = new ArrayList<>(currentQuestion.getOptions());
                    Collections.shuffle(opts);
                    return opts;
                });

                WebSocketMessage questionMsg = new WebSocketMessage();
                questionMsg.setType(WebSocketMessage.NEW_QUESTION);
                questionMsg.setData(Map.of(
                        "questionIndex", qIndex + 1,
                        "totalQuestions", questions.size(),
                        "question", currentQuestion.getQuestion(),
                        "options", options,
                        // include correctAnswer for consistency with existing clients (private answer messages still reveal correctness)
                        "correctAnswer", currentQuestion.getOptions().get(currentQuestion.getCorrectAnswerIndex()),
                        "timeLimit", 30
                ));
                sendMessage(session, questionMsg);

                // Send immediate timer update so the new player sees remaining time
                long remainingMs = room.getQuestionTimeRemaining();
                WebSocketMessage timerMsg = new WebSocketMessage();
                timerMsg.setType(WebSocketMessage.TIMER_UPDATE);
                timerMsg.setData(Map.of(
                        "remaining", (int) (remainingMs / 1000),
                        "answeredCount", room.getAnsweredCount(),
                        "totalPlayers", room.getPlayerCount()
                ));
                sendMessage(session, timerMsg);
            }
        } else if (room.getGameState() == Room.GameState.SHOWING_ANSWER) {
            // If currently showing the answer, send the SHOW_ANSWER payload to the joining session
            List<Question> questions = roomQuestions.get(roomCode);
            if (questions != null) {
                Question currentQuestion = questions.get(room.getCurrentQuestionIndex());
                String correctAnswer = currentQuestion.getOptions().get(currentQuestion.getCorrectAnswerIndex());
                WebSocketMessage showMsg = new WebSocketMessage();
                showMsg.setType(WebSocketMessage.SHOW_ANSWER);
                showMsg.setData(Map.of(
                        "correctAnswer", correctAnswer,
                        "players", getPlayerList(room),
                        "questionIndex", room.getCurrentQuestionIndex() + 1,
                        "totalQuestions", questions.size()
                ));
                sendMessage(session, showMsg);
            }
        } else if (room.getGameState() == Room.GameState.FINISHED) {
            // If game finished, send GAME_OVER to the joining player
            List<Question> questions = roomQuestions.get(roomCode);
            if (questions != null) {
                List<Map<String, Object>> finalScores = room.getPlayers().stream()
                        .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                        .map(p -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("id", p.getId());
                            m.put("name", p.getName());
                            m.put("score", p.getScore());
                            return m;
                        })
                        .toList();

                WebSocketMessage gameOverMsg = new WebSocketMessage();
                gameOverMsg.setType(WebSocketMessage.GAME_OVER);
                gameOverMsg.setData(Map.of(
                        "finalScores", finalScores,
                        "totalQuestions", questions.size()
                ));
                sendMessage(session, gameOverMsg);
            }
        }
    }

    private void handleStartGame(WebSocketSession session, WebSocketMessage message) {
        String roomCode = sessionRoomMap.get(session.getId());
        String playerId = sessionPlayerMap.get(session.getId());
        
        if (roomCode == null) return;
        
        Room room = Room.getRoom(roomCode);
        if (room == null) return;
        
        // Only host can start game
        if (!room.isHost(playerId)) {
            sendError(session, "Only the host can start the game");
            return;
        }

        // Generate questions
        List<Question> questions = questionService.generateQuestions(room.getAgeGroup(), room.getTopic());
        if (questions.isEmpty()) {
            sendError(session, "Failed to generate questions. Try a different topic.");
            return;
        }
        
        roomQuestions.put(roomCode, questions);
        room.setGameState(Room.GameState.PLAYING);
        room.setCurrentQuestionIndex(0);
        
        // Reset all player scores
        room.getPlayers().forEach(p -> {
            p.setScore(0);
            p.resetAnswered();
        });

        // Broadcast game started
        WebSocketMessage startMsg = new WebSocketMessage();
        startMsg.setType(WebSocketMessage.GAME_STARTED);
        startMsg.setData(Map.of(
            "totalQuestions", questions.size(),
            "players", getPlayerList(room)
        ));
        broadcastToRoom(roomCode, null, startMsg);

        // Send first question
        sendQuestion(roomCode, 0);
    }

    private void sendQuestion(String roomCode, int questionIndex) {
        Room room = Room.getRoom(roomCode);
        List<Question> questions = roomQuestions.get(roomCode);
        
        if (room == null || questions == null || questionIndex >= questions.size()) return;

        Question question = questions.get(questionIndex);
        room.setCurrentQuestionIndex(questionIndex);
        room.setGameState(Room.GameState.QUESTION_ACTIVE);
        room.resetAllPlayerAnswers();
        room.startQuestionTimer();

        // Shuffle options for display, but keep track so late joiners see the same order
        List<String> options = new ArrayList<>(question.getOptions());
        String correctAnswer = question.getOptions().get(question.getCorrectAnswerIndex());
        Collections.shuffle(options);
        roomShuffledOptions.put(roomCode, options);

        WebSocketMessage questionMsg = new WebSocketMessage();
        questionMsg.setType(WebSocketMessage.NEW_QUESTION);
        questionMsg.setData(Map.of(
            "questionIndex", questionIndex + 1,
            "totalQuestions", questions.size(),
            "question", question.getQuestion(),
            "options", options,
            "correctAnswer", correctAnswer,
            "timeLimit", 30
        ));
        
        broadcastToRoom(roomCode, null, questionMsg);

        // Start timer
        startQuestionTimer(roomCode);
    }

    private void startQuestionTimer(String roomCode) {
        // Cancel existing timer if any
        ScheduledFuture<?> existingTimer = roomTimers.remove(roomCode);
        if (existingTimer != null) {
            existingTimer.cancel(false);
        }

        // Schedule timer that ticks every second
        ScheduledFuture<?> timer = scheduler.scheduleAtFixedRate(() -> {
            Room room = Room.getRoom(roomCode);
            if (room == null) return;

            long remaining = room.getQuestionTimeRemaining();
            
            // Check if time expired or all answered
            if (remaining <= 0 || room.allPlayersAnswered()) {
                // Cancel this timer
                ScheduledFuture<?> currentTimer = roomTimers.remove(roomCode);
                if (currentTimer != null) {
                    currentTimer.cancel(false);
                }
                showAnswer(roomCode);
            } else {
                // Broadcast timer update
                WebSocketMessage timerMsg = new WebSocketMessage();
                timerMsg.setType(WebSocketMessage.TIMER_UPDATE);
                timerMsg.setData(Map.of(
                    "remaining", (int) (remaining / 1000),
                    "answeredCount", room.getAnsweredCount(),
                    "totalPlayers", room.getPlayerCount()
                ));
                broadcastToRoom(roomCode, null, timerMsg);
            }
        }, 0, 1, TimeUnit.SECONDS);

        roomTimers.put(roomCode, timer);
    }

    private void handleSubmitAnswer(WebSocketSession session, WebSocketMessage message) {
        String roomCode = sessionRoomMap.get(session.getId());
        String playerId = sessionPlayerMap.get(session.getId());
        
        if (roomCode == null || playerId == null) return;
        
        Room room = Room.getRoom(roomCode);
        if (room == null || room.getGameState() != Room.GameState.QUESTION_ACTIVE) return;
        
        Player player = room.getPlayer(playerId);
        if (player == null || player.hasAnswered()) return;

        String answer = (String) message.getData();
        List<Question> questions = roomQuestions.get(roomCode);
        Question currentQuestion = questions.get(room.getCurrentQuestionIndex());
        
        // Check answer
        String correctAnswer = currentQuestion.getOptions().get(currentQuestion.getCorrectAnswerIndex());
        boolean isCorrect = correctAnswer.equalsIgnoreCase(answer.trim());
        
        player.setAnswered(true);
        player.setLastAnswer(answer);
        int pointsAwarded = 0;
        if (isCorrect) {
            // Award points equal to the remaining milliseconds so that a 1ms difference -> different score.
            long remainingMs = room.getQuestionTimeRemaining()/10;
            // Ensure at least 1 point is awarded
            pointsAwarded = (int) Math.max(1, remainingMs);
            player.incrementScore(pointsAwarded);
        }

        // Notify others that player answered
        WebSocketMessage answeredMsg = new WebSocketMessage();
        answeredMsg.setType(WebSocketMessage.PLAYER_ANSWERED);
        answeredMsg.setPlayerId(playerId);
        answeredMsg.setPlayerName(player.getName());
        answeredMsg.setData(Map.of(
            "isCorrect", isCorrect,
            "score", player.getScore(),
            "answeredCount", room.getAnsweredCount(),
            "totalPlayers", room.getPlayerCount(),
            "players", getPlayerList(room)
        ));
        
        // Send to this player specifically (with correct answer)
        WebSocketMessage privateMsg = new WebSocketMessage();
        privateMsg.setType(WebSocketMessage.PLAYER_ANSWERED);
        privateMsg.setPlayerId(playerId);
        privateMsg.setData(Map.of(
            "isCorrect", isCorrect,
            "correctAnswer", correctAnswer,
            "score", player.getScore(),
            "pointsAwarded", pointsAwarded,
            // include millisecond-precision remaining time for clients that want it
            "timeRemainingMs", (int) room.getQuestionTimeRemaining(),
            "timeRemaining", (int) (room.getQuestionTimeRemaining() / 1000)
        ));
        sendMessage(session, privateMsg);
        
        // Broadcast to others (without correct answer)
        broadcastToRoom(roomCode, session.getId(), answeredMsg);
    }

    private void showAnswer(String roomCode) {
        Room room = Room.getRoom(roomCode);
        List<Question> questions = roomQuestions.get(roomCode);
        
        if (room == null || questions == null) return;

        room.setGameState(Room.GameState.SHOWING_ANSWER);
        Question currentQuestion = questions.get(room.getCurrentQuestionIndex());
        String correctAnswer = currentQuestion.getOptions().get(currentQuestion.getCorrectAnswerIndex());

        WebSocketMessage showMsg = new WebSocketMessage();
        showMsg.setType(WebSocketMessage.SHOW_ANSWER);
        showMsg.setData(Map.of(
            "correctAnswer", correctAnswer,
            "players", getPlayerList(room),
            "questionIndex", room.getCurrentQuestionIndex() + 1,
            "totalQuestions", questions.size()
        ));
        broadcastToRoom(roomCode, null, showMsg);

        // Clear shuffled options for this question so next question will regenerate ordering
        roomShuffledOptions.remove(roomCode);
    }

    private void handleNextQuestion(WebSocketSession session, WebSocketMessage message) {
        String roomCode = sessionRoomMap.get(session.getId());
        String playerId = sessionPlayerMap.get(session.getId());
        
        if (roomCode == null) return;
        
        Room room = Room.getRoom(roomCode);
        if (room == null || !room.isHost(playerId)) return;

        int nextIndex = room.getCurrentQuestionIndex() + 1;
        List<Question> questions = roomQuestions.get(roomCode);
        
        if (nextIndex >= questions.size()) {
            // Game over
            endGame(roomCode);
        } else {
            sendQuestion(roomCode, nextIndex);
        }
    }

    private void handlePlayerLeave(WebSocketSession session, WebSocketMessage message) {
        String roomCode = sessionRoomMap.remove(session.getId());
        String playerId = sessionPlayerMap.remove(session.getId());
        sessions.remove(session.getId());

        if (roomCode == null || playerId == null) return;

        Room room = Room.getRoom(roomCode);
        if (room == null) return;

        Player player = room.getPlayer(playerId);
        if (player == null) return;

        room.removePlayer(playerId);

        WebSocketMessage leaveMsg = new WebSocketMessage();
        leaveMsg.setType(WebSocketMessage.PLAYER_LEFT);
        leaveMsg.setPlayerName(player.getName());
        leaveMsg.setData(Map.of(
                "message", player.getName() + " left the room",
                "players", getPlayerList(room)
        ));

        broadcastToRoom(roomCode, null, leaveMsg);

        try {
            if (session.isOpen()) session.close();
        } catch (IOException ignored) {}
    }

    private void endGame(String roomCode) {
        Room room = Room.getRoom(roomCode);
        if (room == null) return;

        room.setGameState(Room.GameState.FINISHED);
        
        // Sort players by score
        List<Map<String, Object>> finalScores = room.getPlayers().stream()
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("score", p.getScore());
                    return m;
                })
                .toList();

        WebSocketMessage gameOverMsg = new WebSocketMessage();
        gameOverMsg.setType(WebSocketMessage.GAME_OVER);
        gameOverMsg.setData(Map.of(
            "finalScores", finalScores,
            "totalQuestions", roomQuestions.get(roomCode).size()
        ));
        broadcastToRoom(roomCode, null, gameOverMsg);

        // Clean up stored state
        roomQuestions.remove(roomCode);
        roomShuffledOptions.remove(roomCode);
        ScheduledFuture<?> timer = roomTimers.remove(roomCode);
        if (timer != null) timer.cancel(false);
    }

    private List<Map<String, Object>> getPlayerList(Room room) {
        return room.getPlayers().stream()
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("score", p.getScore());
                    m.put("hasAnswered", p.hasAnswered());
                    m.put("lastAnswer", p.getLastAnswer());
                    m.put("isHost", room.isHost(p.getId()));
                    return m;
                })
                .toList();
    }

    private WebSocketMessage createPlayerJoinedMessage(Player player, Room room) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(WebSocketMessage.PLAYER_JOINED);
        msg.setPlayerId(player.getId());
        msg.setPlayerName(player.getName());
        msg.setData(Map.of(
            "message", player.getName() + " joined the room",
            "players", getPlayerList(room),
            "playerCount", room.getPlayerCount()
        ));
        return msg;
    }

    private void broadcastToRoom(String roomCode, String excludeSessionId, WebSocketMessage message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            LOG.error("Failed to serialize message: " + e.getMessage(), e);
            return;
        }

        for (Map.Entry<String, String> entry : sessionRoomMap.entrySet()) {
            if (entry.getValue().equals(roomCode) && !entry.getKey().equals(excludeSessionId)) {
                WebSocketSession s = sessions.get(entry.getKey());
                if (s != null && s.isOpen()) {
                    try {
                        s.sendMessage(new TextMessage(json));
                    } catch (IOException e) {
                        LOG.error("Failed to send message: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            LOG.error("Failed to send message: " + e.getMessage(), e);
        }
    }

    private void sendError(WebSocketSession session, String error) {
        sendMessage(session, WebSocketMessage.error(error));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomCode = sessionRoomMap.remove(session.getId());
        String playerId = sessionPlayerMap.remove(session.getId());
        sessions.remove(session.getId());

        if (roomCode != null && playerId != null) {
            Room room = Room.getRoom(roomCode);
            if (room != null) {
                Player player = room.getPlayer(playerId);
                if (player != null) {
                    room.removePlayer(playerId);
                    
                    WebSocketMessage leaveMsg = new WebSocketMessage();
                    leaveMsg.setType(WebSocketMessage.PLAYER_LEFT);
                    leaveMsg.setPlayerName(player.getName());
                    leaveMsg.setData(Map.of(
                        "message", player.getName() + " left the room",
                        "players", getPlayerList(room)
                    ));
                    broadcastToRoom(roomCode, null, leaveMsg);
                }
            }
        }
    }
}
