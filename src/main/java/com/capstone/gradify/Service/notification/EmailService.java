package com.capstone.gradify.Service.notification;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;


@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

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

    public void sendGradeUpdate(String toEmail, String grade, String className, String studentName, String feedbackUrl, String reportDate) throws MessagingException {
        Context context = new Context();
        context.setVariable("grade", grade);
        context.setVariable("className", className);
        context.setVariable("studentName", studentName);
        context.setVariable("feedbackUrl", feedbackUrl);
        context.setVariable("reportDate", reportDate);

        // Template name: "grade-update" (create this Thymeleaf template under resources/templates)
        String htmlContent = templateEngine.process("grade-update", context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setTo(toEmail);
        helper.setSubject("Grade Update");
        helper.setText(htmlContent, true);
        log.info("Sending Grade Update");
        mailSender.send(message);
    }
}

