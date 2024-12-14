package de.bsi.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

@Service
public class JiraService {

    public void createJiraTicket(JiraTicketRequest ticketRequest) throws JsonProcessingException {
        String email = "youssef.ayari1@esprit.tn";
        String token = "ATATT3xFfGF0eGQeoapTnetqNrkWu_auqdpzyFGwJT8bVYJgFAgtrLuWUHZjaIJjaoEhHriz1QROiMcga6sErMlC7cSdMdu1mkUVK57xq41RIvLf1y_IhOWTTqpmodFFWApQTRSZqo5Py_5e5JxNptd2dywnqV0SdYWkZsghZI21r9_USTd2R5k=1CA02FC0";
        String jiraDomain = "https://travel-bridge.atlassian.net";
        String authHeader = "Basic " + Base64.getEncoder().encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonBody = objectMapper.writeValueAsString(ticketRequest);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                jiraDomain + "/rest/api/2/issue",
                HttpMethod.POST,
                entity,
                String.class);

        System.out.println(response.getBody());
    }
}
