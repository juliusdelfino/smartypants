package com.delfino.smartypants.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.delfino.smartypants.model.Question;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OllamaQuestionService {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaQuestionService.class);

    private final String ollamaUrl;

    private final String ollamaModel;

    private final RestTemplate restTemplate;

    private static final String TRIVIA_MD_PATH = "trivia.md";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String promptTemplate;

    public OllamaQuestionService(RestTemplate restTemplate,
                                 @Value("${ollama.url}") String ollamaUrl,
                                 @Value("${ollama.model}") String ollamaModel) {
        this.restTemplate = restTemplate;
        this.ollamaUrl = ollamaUrl;
        this.ollamaModel = ollamaModel;
        loadPromptTemplate();
    }

    private void loadPromptTemplate() {
        try {
            // Try to load from project root directory
            Path path = Path.of(TRIVIA_MD_PATH);
            if (Files.exists(path)) {
                promptTemplate = Files.readString(path, StandardCharsets.UTF_8);
                LOG.info("Loaded prompt template from: {}", path.toAbsolutePath());
            } else {
                // Fallback to classpath resource
                ClassPathResource resource = new ClassPathResource(TRIVIA_MD_PATH);
                if (resource.exists()) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        promptTemplate = sb.toString();
                        LOG.info("Loaded prompt template from classpath");
                    }
                } else {
                    LOG.error("Could not find trivia.md, using fallback template");
                    promptTemplate = FALLBACK_TEMPLATE;
                }
            }
        } catch (IOException e) {
            LOG.error("Error loading trivia.md: " + e.getMessage(), e);
            promptTemplate = FALLBACK_TEMPLATE;
        }
    }

    private static final String FALLBACK_TEMPLATE = """
You are a trivia question generator. Your task is to create engaging, accurate trivia questions.

STRICT REQUIREMENTS:
1. First, verify that the topic '[topic]' is well-known, wholesome and based on facts. If the topic is inappropriate, obscure, or fictional in a way that makes factual questions impossible, return an empty JSON array: []

2. Generate exactly 10 trivia questions for age group '[age-group]' on the topic '[topic]'.

3. Each question must be appropriate for the specified age group.

4. Response format must be a strict JSON array. Each object contains:
   - "question": String, the trivia question text
   - "options": Array of exactly 4 strings (1 correct answer followed by 3 incorrect answers)
   - "correctAnswerIndex": Integer 0-3, indicating which option is correct

5. All questions must be factual and verifiable.

Generate exactly 10 questions now.""";
    

    public List<Question> generateQuestions(String ageGroup, String topic) {
        LOG.info("Generating questions from Ollama with params:  ageGroup = {}, topic = {}", ageGroup, topic);
        try {
//            if (true) throw new IllegalArgumentException("Simulated error");
            String prompt = buildPrompt(ageGroup, topic);
            String response = callOllama(prompt);
            return parseQuestions(response);
        } catch (Exception e) {
            LOG.error("Error generating questions from Ollama: " + e.getMessage(), e);
            return getFallbackQuestions(ageGroup, topic);
        }
    }

    private String buildPrompt(String ageGroup, String topic) {
        if (promptTemplate == null) {
            promptTemplate = FALLBACK_TEMPLATE;
        }
        return promptTemplate
                .replace("[topic]", topic)
                .replace("[age-group]", ageGroup);
    }

    private String callOllama(String prompt) throws IOException {

        Map<String, Object> body = Map.of(
                "model", ollamaModel,
                "prompt", prompt,
                "stream", false
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(System.getenv("OLLAMA_API_KEY"));
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                ollamaUrl,
                request,
                String.class
        );

        String responseText = response.getBody();
        String dateTime = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(ZonedDateTime.now(ZoneOffset.UTC));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("./data/ollama_request_" + dateTime + ".json").toFile(), objectMapper.readTree(responseText));
        responseText = responseText.replace("```json", "").replace("```", "");

        // Ollama returns a JSON with "response" field
        Map<String, Object> ollamaResponse = objectMapper.readValue(responseText, new TypeReference<>() {});
        Object generated = ollamaResponse.get("response");
        return generated != null ? generated.toString() : "[]";
    }

    private List<Question> parseQuestions(String response) {
        try {
            // Clean up the response - remove markdown code blocks if present
            String cleaned = response.trim();
            if (cleaned.startsWith("```")) {
                int start = cleaned.contains("[") ? cleaned.indexOf("[") : cleaned.indexOf("\n");
                int end = cleaned.lastIndexOf("```");
                if (start != -1 && end != -1 && end > start) {
                    cleaned = cleaned.substring(start, end).trim();
                } else if (cleaned.startsWith("```json")) {
                    cleaned = cleaned.substring(7).replace("```", "").trim();
                } else {
                    cleaned = cleaned.replace("```", "").trim();
                }
            }

            // Find the JSON array in the response
            int arrayStart = cleaned.indexOf('[');
            int arrayEnd = cleaned.lastIndexOf(']');
            if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
                cleaned = cleaned.substring(arrayStart, arrayEnd + 1);
            }

            List<OllamaQuestion> ollamaQuestions = objectMapper.readValue(cleaned, new TypeReference<>() {});
            
            if (ollamaQuestions == null || ollamaQuestions.isEmpty()) {
                return Collections.emptyList();
            }

            List<Question> questions = new ArrayList<>();
            for (OllamaQuestion oq : ollamaQuestions) {
                if (oq.getOptions() != null && oq.getOptions().size() == 4 && 
                    oq.getCorrectAnswerIndex() >= 0 && oq.getCorrectAnswerIndex() < 4) {
                    questions.add(new Question(oq.getQuestion(), oq.getOptions(), oq.getCorrectAnswerIndex()));
                }
            }
            return questions;
        } catch (Exception e) {
            LOG.error("Failed to parse questions: " + e.getMessage(), e);
            LOG.info("Raw response: {}", response);
            return Collections.emptyList();
        }
    }

    private List<Question> getFallbackQuestions(String ageGroup, String topic) {
        List<Question> fallback = new ArrayList<>();
        fallback.add(new Question(
            "What is the most common topic for trivia?",
            Arrays.asList(topic, "Sports", "History", "Science"),
            0
        ));
        fallback.add(new Question(
            "What age group is this game designed for?",
            Arrays.asList(ageGroup, "Kids", "Teens", "Seniors"),
            0
        ));
        fallback.add(new Question(
            "Which is a popular trivia game format?",
            Arrays.asList("Multiple Choice", "True/False", "Fill in blank", "Essay"),
            0
        ));
        fallback.add(new Question(
            "What makes a good trivia question?",
            Arrays.asList("Clear and factual", "Opinion-based", "Too obscure", "Too easy"),
            0
        ));
        fallback.add(new Question(
            "How many questions are in this game?",
            Arrays.asList("10", "5", "15", "20"),
            0
        ));
        fallback.add(new Question(
            "What is the purpose of trivia games?",
            Arrays.asList("To test knowledge", "To waste time", "To confuse people", "To sleep"),
            0
        ));
        fallback.add(new Question(
            "Which of these is a valid trivia topic?",
            Arrays.asList(topic, "Made-up topic", "Nonsense", "Random gibberish"),
            0
        ));
        fallback.add(new Question(
            "What is a key feature of good trivia?",
            Arrays.asList("Interesting facts", "Boring details", "Wrong answers", "Confusion"),
            0
        ));
        fallback.add(new Question(
            "How should trivia questions be written?",
            Arrays.asList("Clear and concise", "Vague and unclear", "Very long", "Confusing"),
            0
        ));
        fallback.add(new Question(
            "What is the best way to learn from trivia?",
            Arrays.asList("Play regularly", "Never play", "Ignore facts", "Skip questions"),
            0
        ));
        return fallback;
    }

    // Inner class for parsing Ollama response
    public static class OllamaQuestion {
        private String question;
        private List<String> options;
        private int correctAnswerIndex;

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        public List<String> getOptions() { return options; }
        public void setOptions(List<String> options) { this.options = options; }
        public int getCorrectAnswerIndex() { return correctAnswerIndex; }
        public void setCorrectAnswerIndex(int correctAnswerIndex) { this.correctAnswerIndex = correctAnswerIndex; }
    }
}
