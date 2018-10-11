package com.jacobsonmt.idrbind.controllers;

import com.jacobsonmt.idrbind.model.ContactForm;
import com.jacobsonmt.idrbind.services.EmailService;
import com.jacobsonmt.idrbind.services.JobManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Log4j2
@Controller
public class MainController {

    @Autowired
    private JobManager jobManager;

    @Autowired
    private EmailService emailService;


    @GetMapping("/")
    public String index( Model model) {

        return "index";
    }

    @GetMapping("/queue")
    public String queue( Model model) {

        model.addAttribute("jobs", jobManager.listPublicJobs() );

        return "queue";
    }

    @GetMapping("/documentation")
    public String documentation( Model model) {
        return "documentation";
    }

    @GetMapping("/faq")
    public String faq( Model model) {
        return "faq";
    }

    @GetMapping("/contact")
    public String contact( Model model) {
        model.addAttribute("contactForm", new ContactForm());
        return "contact";
    }

    @PostMapping("/contact")
    public String contact( Model model,
                           HttpServletRequest request,
                           @Valid ContactForm contactForm,
                           BindingResult bindingResult ) {
        if (bindingResult.hasErrors()) {
            return "contact";
        }

        log.info( contactForm );
        try {
            emailService.sendSupportMessage( contactForm.getMessage(), contactForm.getName(), contactForm.getEmail(), request, contactForm.getAttachment() );
            model.addAttribute( "message", "Sent. We will get back to you shortly." );
            model.addAttribute( "success", true );
        } catch ( MessagingException | MailSendException e) {
            log.error(e);
            model.addAttribute( "message", "There was a problem sending the support request. Please try again later." );
            model.addAttribute( "success", false );
        }

        return "contact";
    }
}
