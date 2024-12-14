package de.bsi.openai;

import de.bsi.openai.chatgpt.PingController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ScheduledTasks {

    @Autowired
    PingController pingController;

    @Autowired
    private RestTemplate restTemplate;

    @Scheduled(fixedRate = 5000)
    public void pingPeriodiquement() {
        String url = "https://gpt-mail-1.onrender.com/health";
        String response = restTemplate.getForObject(url, String.class);
        System.out.println(response);
    }
}
