package de.bsi.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JiraTicketParser {

    public static List<JiraTicketRequest> parseTickets(String inputString) {
        List<JiraTicketRequest> tickets = new ArrayList<>();
        Pattern pattern = Pattern.compile("Ticket\\s*(\\d+)\\s*:\\s*Summary\\s*:\\s*(.*?)\\s*Description\\s*:\\s*(.*?)\\s*IssueType\\s*:\\s*(\\w+)");

        Matcher matcher = pattern.matcher(inputString);

        while (matcher.find()) {
            JiraTicketRequest ticket = new JiraTicketRequest();

            ticket.setNumberTicket(Integer.parseInt(matcher.group(1)));

            JiraTicketRequest.Fields fields = new JiraTicketRequest.Fields();

            JiraTicketRequest.Project project = new JiraTicketRequest.Project();

            project.setKey("TB");

            JiraTicketRequest.IssueType issueTypeJira = new JiraTicketRequest.IssueType();

            issueTypeJira.setName(matcher.group(4).trim());

            fields.setProject(project);

            fields.setSummary(matcher.group(2).trim());

            fields.setDescription(matcher.group(3).trim());

            fields.setIssuetype(issueTypeJira);

            // Attribuer l'objet Fields à l'objet ticket
            ticket.setFields(fields);

            tickets.add(ticket);
        }

        return tickets;
    }
}
