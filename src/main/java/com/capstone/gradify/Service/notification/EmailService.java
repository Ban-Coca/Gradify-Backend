package com.capstone.gradify.Service.notification;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;


@Service
public class EmailService {
    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private TemplateEngine templateEngine;

    public void sendVerificationEmail(String toEmail, String verificationCode) throws MessagingException {
        Context context = new Context();
        context.setVariable("verificationCode", verificationCode);

        String htmlContent = templateEngine.process("verification-code", context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setTo(toEmail);
        helper.setSubject("Email Verification Code");
        helper.setText(htmlContent, true); // true indicates that the text is HTML
        mailSender.send(message);
    }

    public void sendFeedbackNotification(String toEmail, String subject, String feedback, String className, String studentName, String feedbackUrl, String reportDate) throws MessagingException {
        Context context = new Context();
        context.setVariable("feedback", feedback);
        context.setVariable("subject", subject);
        context.setVariable("feedbackUrl", feedbackUrl);
        context.setVariable("className", className);
        context.setVariable("studentName", studentName);
        context.setVariable("reportDate", reportDate);

        String htmlContent = templateEngine.process("feedback-notification", context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(htmlContent, true); // true indicates that the text is HTML
        mailSender.send(message);
    }
}

