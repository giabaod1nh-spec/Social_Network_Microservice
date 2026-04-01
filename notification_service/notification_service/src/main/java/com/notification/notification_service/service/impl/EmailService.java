package com.notification.notification_service.service.impl;

import com.notification.notification_service.service.IEmailService;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "EMAIL_SERVICE")
public class EmailService implements IEmailService {
    private final SendGrid sendGrid;

    @Value("${spring.sendgrid.from-email}")
    private String from;

    @Value("${app.verification-link-base}")
    private String verifyLinkBase;

    @Override
    public void emailVerfication(String userEmail, String userName, String token) throws IOException {
        log.info("Email verification started");

        Email fromEmail = new Email(from);
        Email toEmail = new Email(userEmail);
        String subject = "Xác thực tài khoản";

        String verificationLink = verifyLinkBase + "/auth/verify-email?token=" + token;

        Map<String , String> map = new HashMap<>();
        map.put("name" , userName);
        map.put("verification-link" , verificationLink);

        Mail mail = new Mail();
        mail.setFrom(fromEmail);
        mail.setSubject(subject);

        Personalization personalization =new Personalization();
        personalization.addTo(toEmail);

        //Add to dynamic data
        map.forEach(personalization::addDynamicTemplateData);

        mail.addPersonalization(personalization);
        mail.setTemplateId("d-85c30993843c4a36b41414aacdc1fcac");

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());
        //response cua request gui di
        Response response = sendGrid.api(request);

        if (response.getStatusCode() >= 200 ) {
            log.info("Email sent successfully  {}", toEmail);
        } else {
            log.error("Failed to send email. Status:{}, Body: {}",
                    response.getStatusCode(), response.getBody());
            throw new RuntimeException("Failed to send email: " + response.getBody());
        }
    }
}
