package de.bsi.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bsi.openai.chatgpt.ChatCompletionResponse;
import de.bsi.openai.chatgpt.CompletionRequest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.sql.*;
import java.util.List;
import java.util.Properties;


@Service
@EnableAsync
public class EmailConf {

    @Autowired
    private ObjectMapper jsonMapper;

    @Autowired
    private OpenAiApiClient client;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private JiraService jiraService;

    private static final String EMAIL = "bottalkermail@gmail.com";
    private static final String PASSWORD = "bouq utxn aefu ispa";
    private static final String GPT_MODEL = "gpt-4-1106-preview";

    private Session session;

    @Scheduled(fixedDelay = 2000)
    public void checkForNewMessages() throws InterruptedException {

        Properties properties = new Properties();
        properties.setProperty("mail.store.protocol", "imaps");
        properties.setProperty("mail.imaps.host", "imap.gmail.com");
        properties.setProperty("mail.imaps.port", "993");
        properties.setProperty("mail.imaps.connectiontimeout", "5000");
        properties.setProperty("mail.imaps.timeout", "5000");
        properties.setProperty("mail.imaps.ssl.enable", "true");
        properties.setProperty("mail.imaps.ssl.trust", "*");
        session = Session.getInstance(properties, null);

        try (Store store = session.getStore("imaps")) {
            int maxAttempts = Integer.MAX_VALUE;
            int attempts = 0;
            boolean connected = false;

            while (!connected && attempts < maxAttempts) {
                try {
                    store.connect("imap.gmail.com", EMAIL, PASSWORD);
                    connected = true;
                } catch (MessagingException e) {
                    e.printStackTrace();
                    Thread.sleep(5000); // Attendre 5 secondes avant de réessayer
                    attempts++;
                }
            }

            if (connected) {
                try (Folder inbox = store.getFolder("inbox")) {
                    inbox.open(Folder.READ_ONLY);

                    inbox.addMessageCountListener(new MessageCountAdapter() {
                        @Override
                        public void messagesAdded(final MessageCountEvent ev) {
                            Message[] messages = ev.getMessages();

                            for (Message message : messages) {
                                // Traitement du message
                                processMessage(message);
                            }
                        }
                    });

                    while (true) {
                        // Vérifier périodiquement les nouveaux messages
                        inbox.getMessageCount();
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Rétablir le statut d'interruption du thread
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("Échec de la connexion après plusieurs tentatives.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Async
    public void processMessage(final Message message) {
        try {
            final String from = InternetAddress.toString(message.getFrom());
            final String subject = message.getSubject();
            final Object content = message.getContent();
            Integer countMailSend = 0;
            if (content instanceof Multipart) {
                final Multipart multipart = (Multipart) content;

                for (int i = 0; i < multipart.getCount(); i++) {
                    final BodyPart bodyPart = multipart.getBodyPart(i);

                    if (isPdfAttachment(bodyPart)) {
                        System.out.println("Process PDF mail message.");
                        processPdfAttachment(bodyPart, from, subject, message);
                    } else if (isExcelAttachment(bodyPart)) {
                        System.out.println("Process EXCEL mail message.");
                        processExcelAttachment(bodyPart, from, subject, message);
                    }else if (isWordAttachment(bodyPart)) {
                        System.out.println("Process WORD mail message.");
                        processWordAttachment(bodyPart, from, subject, message);
                    } else if (bodyPart.getContent() instanceof String && bodyPart.getContent() != null && countMailSend < 1) {
                        System.out.println("Process simple mail message.");
                        processTextMessage((String) bodyPart.getContent(), from, subject, message);
                    }
                    countMailSend ++;
                }
                countMailSend = 0;
            }
        } catch (final Exception e) {
            handleException("Erreur lors du traitement du message.", e);
        }
    }

    private void processExcelAttachment(final BodyPart bodyPart, final String from, final String subject, final Message originalMessage) {
        try {
            final String excelContent = extractExcelContent(bodyPart);
            final String response = generateChatGPTResponse(excelContent, from);
            sendEmail(originalMessage, subject, response);
        } catch (Exception e) {
            handleException("Erreur lors du traitement de la pièce jointe Excel.", e);
        }
    }

    private void processWordAttachment(final BodyPart bodyPart, final String from, final String subject, final Message originalMessage) {
        try {
            final String extractedText = extractTextFromWordAttachment(bodyPart);
            if (extractedText.isEmpty()) {
                System.out.println("Le document Word est vide ou ne peut être lu.");
                return;
            }
            final String response = generateChatGPTResponse(extractedText, from);
            sendEmail(originalMessage, subject, response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String extractTextFromWordAttachment(final BodyPart bodyPart) throws IOException, MessagingException {
        StringBuilder extractedText = new StringBuilder();
        try (InputStream inputStream = bodyPart.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream)) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph para : paragraphs) {
                extractedText.append(para.getText()).append("\n");
            }
        }
        return extractedText.toString();
    }

    private boolean isWordAttachment(Part bodyPart) throws MessagingException {
        final String fileName = bodyPart.getFileName();

        return fileName != null &&
                (fileName.toLowerCase().endsWith(".doc") || fileName.toLowerCase().endsWith(".docx"));
    }

    private boolean isExcelAttachment(BodyPart bodyPart) throws MessagingException {
        final String fileName = bodyPart.getFileName();

        return fileName != null && (fileName.toLowerCase().endsWith(".xls") || fileName.toLowerCase().endsWith(".xlsx"));
    }


    private String extractExcelContent(final BodyPart bodyPart) {
        try (final InputStream inputStream = bodyPart.getInputStream()) {

            HSSFWorkbook workbook = new HSSFWorkbook(inputStream);
            HSSFSheet sheet = workbook.getSheetAt(0);
            StringBuilder content = new StringBuilder();

            for (Row row : sheet) {
                for (Cell cell : row) {
                    switch (cell.getCellType()) {
                        case STRING -> content.append(cell.getStringCellValue()).append(" ");
                        case NUMERIC -> content.append(cell.getNumericCellValue()).append(" ");
                        case BOOLEAN -> content.append(cell.getBooleanCellValue()).append(" ");

                        // Vous pouvez gérer d'autres types de cellules selon vos besoins
                        default -> content.append(cell.toString()).append(" ");
                    }
                }
                content.append("\n");
            }

            workbook.close();
            return content.toString().trim();
        } catch (final Exception e) {
            handleException("Erreur lors de l'extraction du contenu Excel.", e);
            return "";
        }
    }

    private void processPdfAttachment(final BodyPart bodyPart, final String from, final String subject, final Message originalMessage) {
        try {
            final String pdfContent = extractPdfContent(bodyPart);
            final String response = generateChatGPTResponse(pdfContent, from);
            sendEmail(originalMessage, subject, response);
        } catch (Exception e) {
            handleException("Erreur lors du traitement de la pièce jointe PDF.", e);
        }
    }

    private void processTextMessage(final String textMessage, final String from, final String subject, final Message originalMessage) {
        try {
                final String response = generateChatGPTResponse(textMessage, subject);
                sendEmail(originalMessage, subject, response);

        } catch (final Exception e) {
            handleException("Erreur lors du traitement du message texte.", e);
        }
    }

    private boolean isPdfAttachment(BodyPart bodyPart) throws MessagingException {
        final String fileName = bodyPart.getFileName();
        final String contentType = bodyPart.getContentType();

        return fileName != null && fileName.toLowerCase().endsWith(".pdf") &&
                contentType != null && contentType.toLowerCase().startsWith("application/pdf");
    }

    private String extractPdfContent(final BodyPart bodyPart) {
        try (final InputStream inputStream = bodyPart.getInputStream();
             final PDDocument document = PDDocument.load(inputStream)) {
             final PDFTextStripper textStripper = new PDFTextStripper();
            return textStripper.getText(document);
        } catch (final Exception e) {
            handleException("Erreur lors de l'extraction du contenu PDF.", e);
            return "";
        }
    }

    private String generateChatGPTResponse(final String message, final String subject) {
        try {
            if(subject.contains("jira") || subject.contains("JIRA")){
                String promptJira = "Basé sur cette mail et besoin : " + message + ", peux-tu générer des suggestions pour des tickets JIRA qui " +
                        "répondraient à ces besoins ? Chaque ticket doit suivre ce format : Ticket [Numéro(commence par 1)] : " +
                        "Summary : [Résumé du problème ou de la demande d'amélioration] " +
                        "Description : [Description détaillée du problème ou de la fonctionnalité demandée] " +
                        "IssueType : [Type de ticket, par exemple 'Bug', ou 'Task']";

                final CompletionRequest.Message userMessage = new CompletionRequest.Message("user", promptJira);
                final List<CompletionRequest.Message> messages = List.of(userMessage);
                final CompletionRequest request = new CompletionRequest(GPT_MODEL, messages, 0.7);

                final String postBodyJson = jsonMapper.writeValueAsString(request);
                final String responseBody = client.postToOpenAiApi(postBodyJson, OpenAiApiClient.OpenAiService.GPT_4);


                ObjectMapper mapper = new ObjectMapper();
                final ChatCompletionResponse response = mapper.readValue(responseBody, ChatCompletionResponse.class);

                final String assistantResponse = response.getChoices()[0].getMessage().getContent();
                if(!assistantResponse.isEmpty()) {
                    List<JiraTicketRequest> jiraTicketRequestList = JiraTicketParser.parseTickets(assistantResponse);
                    for (int i = 0; i <= jiraTicketRequestList.toArray().length - 1; i++) {
                        jiraService.createJiraTicket(jiraTicketRequestList.get(i));
                    }

                    System.out.println("Ticket jira number list : " + jiraTicketRequestList.size());
                }
                return assistantResponse;

            }
            final CompletionRequest.Message userMessage = new CompletionRequest.Message("user", message);
            final List<CompletionRequest.Message> messages = List.of(userMessage);
            final CompletionRequest request = new CompletionRequest(GPT_MODEL, messages, 0.7);

            final String postBodyJson = jsonMapper.writeValueAsString(request);
            final String responseBody = client.postToOpenAiApi(postBodyJson, OpenAiApiClient.OpenAiService.GPT_4);

            ObjectMapper mapper = new ObjectMapper();

            JsonNode jsonNode = mapper.readTree(responseBody);

            String content = jsonNode.path("choices").get(0).path("message").path("content").asText();
            
            return content;
        } catch (Exception e) {
            handleException("Erreur lors de la génération de la réponse de ChatGPT.", e);
            return "";
        }
    }

    public void sendEmail(Message originalMessage, String subject, String response) {
        String email = "bottalkermail@gmail.com";
        String password = "bouq utxn aefu ispa";

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.ssl.trust", "*");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(email, password);
            }
        });

        try {
            MimeMessage replyMessage = new MimeMessage(session);
            replyMessage.setFrom(new InternetAddress(email));

            // Répondre au "Reply-To" ou "From" si "Reply-To" n'est pas défini
            Address[] replyToAddresses = originalMessage.getReplyTo();
            if (replyToAddresses != null && replyToAddresses.length > 0) {
                replyMessage.setRecipient(Message.RecipientType.TO, replyToAddresses[0]);
            } else {
                replyMessage.setRecipient(Message.RecipientType.TO, originalMessage.getFrom()[0]);
            }

            // Ajouter les adresses CC
            Address[] ccAddresses = originalMessage.getRecipients(Message.RecipientType.CC);
            if (ccAddresses != null && ccAddresses.length > 0) {
                replyMessage.setRecipients(Message.RecipientType.CC, ccAddresses);
            }

            // Configurer l'en-tête pour maintenir la même discussion
            String messageId = ((MimeMessage) originalMessage).getMessageID();
            replyMessage.setHeader("In-Reply-To", messageId);
            replyMessage.setHeader("References", messageId);

            // Mettre en place le "Subject" et le "Content"
            replyMessage.setSubject(subject);
            replyMessage.setText(response);

            // Envoyer le message
            Transport.send(replyMessage);
            System.out.println("Réponse au message envoyée avec succès.");
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    private void handleException(String message, Exception e) {
        System.err.println(message);
        e.printStackTrace();
    }




    public void configureDataBase() throws SQLException {

        // Informations de connexion à la base de données PostgreSQL
        String url = "jdbc:postgresql://dpg-clcemgjmot1c73dfmjm0-a.oregon-postgres.render.com/ayarinho";
        String utilisateur = "youssef";
        String motDePasse = "I0yyHDMiLENpCfqsbSiyanQjFMbBt422";

        // Établir une connexion à la base de données PostgreSQL
        Connection connexion = DriverManager.getConnection(url, utilisateur, motDePasse);

        // Requête SQL pour récupérer le fichier inséré (vous pouvez remplacer 1 par l'ID approprié)
        String requeteSelect = "SELECT fichier FROM trainsetdata WHERE id = (SELECT MAX(id) FROM trainsetdata);";
        PreparedStatement preparedStatementSelect = connexion.prepareStatement(requeteSelect);

        ResultSet resultSet = preparedStatementSelect.executeQuery();
    }
}
