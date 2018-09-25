package com.jacobsonmt.idrbind.controllers;

import com.jacobsonmt.idrbind.services.JobManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

@Log4j2
@Controller
public class MainController {

    @Autowired
    private JobManager jobManager;

    @GetMapping("/")
    public String index( Model model) throws IOException {

        return "index";
    }

    @GetMapping("/queue")
    public String queue( Model model) throws IOException {

        model.addAttribute("jobs", jobManager.listPublicJobs() );

        return "queue";
    }

    @GetMapping("/documentation")
    public String documentation( Model model) throws IOException {
        return "documentation";
    }

    @GetMapping("/faq")
    public String faq( Model model) throws IOException {
        return "faq";
    }

    @GetMapping("/contact")
    public String contact( Model model) throws IOException {
        return "contact";
    }
}
