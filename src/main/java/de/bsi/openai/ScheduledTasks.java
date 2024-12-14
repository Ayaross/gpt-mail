package de.bsi.openai;

import de.bsi.openai.chatgpt.PingController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    @Autowired
    PingController pingController;

    @Scheduled(fixedRate = 5000)
    public void pingPeriodiquement() {
        pingController.healthCheck();
    }
}
