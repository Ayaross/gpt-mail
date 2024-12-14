package de.bsi.openai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ScheduledTasks {
    @Autowired
    private RestTemplate restTemplate;

    //@Scheduled(fixedRate = 5000)
    public void pingPeriodiquement() {
        String url = "https://gpt-mail-51yb.onrender.com";
        String response = restTemplate.getForObject(url, String.class);
        System.out.println(response);
    }
}
