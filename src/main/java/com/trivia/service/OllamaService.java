package com.trivia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Service
public class OllamaService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public String generateTrivia(String ageGroup, String topic) throws Exception {
        // Read the prompt template
        String promptTemplate = Files.readString(Path.of("src/main/resources/trivia.md"));

        // Replace placeholders
        String prompt = promptTemplate
                .replace("[age-group]", ageGroup)
                .replace("[topic]", topic);

        // Prepare the request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gemma3:latest");
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);
        requestBody.put("format", "json");

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        // Create HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        // Send request and get response
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama API returned status code: " + response.statusCode());
        }

        // Parse response
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        return jsonResponse.get("response").asText();
    }
}
