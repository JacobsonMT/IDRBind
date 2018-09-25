package com.jacobsonmt.idrbind.services;

import com.jacobsonmt.idrbind.model.IDRBindJob;
import com.jacobsonmt.idrbind.settings.SiteSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender emailSender;

    @Autowired
    SiteSettings siteSettings;

    private void sendMessage( String subject, String content, String to ) throws MessagingException {
        sendMessage( subject, content, to, null );
    }

    private void sendMessage( String subject, String content, String to, MultipartFile attachment ) throws MessagingException {

        MimeMessage message = emailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper( message, true );

        helper.setSubject( subject );
        helper.setText( content, true );
        helper.setTo( to );
        helper.setFrom( siteSettings.getAdminEmail() );

        if ( attachment != null ) {
            helper.addAttachment( attachment.getOriginalFilename(), attachment );
        }

        emailSender.send( message );

    }

    public void sendSupportMessage( String message, String name, String email, HttpServletRequest request,
                                    MultipartFile attachment ) throws MessagingException {
        String content =
                "Name: " + name + "\r\n" +
                        "Email: " + email + "\r\n" +
                        "User-Agent: " + request.getHeader( "User-Agent" ) + "\r\n" +
                        "Message: " + message + "\r\n" +
                        "File Attached: " + String.valueOf( attachment != null && !attachment.getOriginalFilename().equals( "" ) );

        sendMessage( "IDR Bind Help - Contact Support", content, siteSettings.getAdminEmail(), attachment );
    }

    public void sendJobStartMessage( IDRBindJob job ) throws MessagingException {
        if ( job.getEmail() == null || job.getEmail().isEmpty() ) {
            return;
        }
        StringBuilder content = new StringBuilder();
        content.append( "<p>Job Submitted</p>" );
        content.append( "<p>Label: " + job.getLabel() + "</p>" );
        content.append( "<p>Submitted: " + job.getSubmittedDate() + "</p>" );
        content.append( "<p>Status: " + job.getStatus() + "</p>" );
        if ( job.isSaved() ) {
            content.append( "<p>Saved Link: " + "<a href='" + siteSettings.getFullUrl()
                    + "job/" + job.getJobId() + "' target='_blank'>"
                    + siteSettings.getFullUrl() + "job/" + job.getJobId() + "'</a></p>" );
        }

        sendMessage( "IDB Bind - Job Submitted", content.toString(), job.getEmail() );
    }

    public void sendJobCompletionMessage( IDRBindJob job ) throws MessagingException {
        if ( job.getEmail() == null || job.getEmail().isEmpty() ) {
            return;
        }
        StringBuilder content = new StringBuilder();
        content.append( "<p>Job Complete</p>" );
        content.append( "<p>Label: " + job.getLabel() + "</p>" );
        content.append( "<p>Submitted: " + job.getSubmittedDate() + "</p>" );
        content.append( "<p>Status: " + job.getStatus() + "</p>" );
        if ( job.isSaved() ) {
            content.append( "<p>Saved Link: " + "<a href='" + siteSettings.getFullUrl()
                    + "job/" + job.getJobId() + "' target='_blank'>"
                    + siteSettings.getFullUrl() + "job/" + job.getJobId() + "'</a></p>" );
        }

        sendMessage( "IDB Bind - Job Complete", content.toString(), job.getEmail() );
    }

}