package com.jacobsonmt.idrbind.controllers;

import com.jacobsonmt.idrbind.model.IDRBindJob;
import com.jacobsonmt.idrbind.model.IDRBindJobResult;
import com.jacobsonmt.idrbind.services.JobManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Log4j2
@Controller
public class JobController {

    @Autowired
    private JobManager jobManager;



    @PostMapping("/")
    public String submitJob(@RequestParam("pdbFile") MultipartFile pdbFile,
                                   @RequestParam("fastaFile") MultipartFile fastaFile,
                                   @RequestParam("proteinChainIds") String proteinChainIds,
                                   @RequestParam("label") String label,
                                   @RequestParam(value = "email", required = false, defaultValue = "") String email,
                                   @RequestParam(value = "hidden", required = false, defaultValue = "false") boolean hidden,
                                   HttpServletRequest request,
                                   RedirectAttributes redirectAttributes) throws IOException {

        String ipAddress = request.getHeader( "X-FORWARDED-FOR" );
        if ( ipAddress == null ) {
            ipAddress = request.getRemoteAddr();
        }

        IDRBindJob job = jobManager.createJob( ipAddress,
                label,
                IDRBindJob.inputStreamToString( pdbFile.getInputStream() ),
                IDRBindJob.inputStreamToString( fastaFile.getInputStream() ),
                proteinChainIds,
                email,
                hidden );
        String msg = jobManager.submit( job );

        if (msg.isEmpty()) {
            redirectAttributes.addFlashAttribute( "message",
                    "Job Submitted! View job <a href='job/" + job.getJobId() + "' target='_blank'>here</a>." );
            redirectAttributes.addFlashAttribute( "warning", false );
        } else {
            redirectAttributes.addFlashAttribute( "message", msg );
            redirectAttributes.addFlashAttribute( "warning", true );
        }

        return "redirect:/";
    }

    @GetMapping("/job/{jobId}")
    public String job( @PathVariable("jobId") String jobId,
                       Model model) throws IOException {

        IDRBindJob job = jobManager.getSavedJob( jobId );

        if (job==null) {
            return "/";
        }

        model.addAttribute("job", jobManager.getSavedJob( jobId ).toValueObject( true ) );

        return "job";
    }

    @GetMapping("/job/{jobId}/resultPDB")
    public ResponseEntity<String> jobResultPDB( @PathVariable("jobId") String jobId) {
        IDRBindJob job = jobManager.getSavedJob( jobId );

        // test for not null and complete
        if ( job != null && job.isComplete() && !job.isFailed() ) {
            try {
                IDRBindJobResult result = job.getFuture().get( 1, TimeUnit.SECONDS );

                    return ResponseEntity.ok()
                            .contentType( MediaType.parseMediaType("application/octet-stream"))
                            .header( HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + job.getLabel() + "-result.pdb\"")
                            .body(result.getResultPDB());

            } catch ( InterruptedException | ExecutionException | TimeoutException e ) {
                log.warn( e );
                ResponseEntity.status( 500 ).body( "" );
            }
        }
        return ResponseEntity.badRequest().body( "" );
    }

    @GetMapping("/job/{jobId}/resultCSV")
    public ResponseEntity<String> jobResultCSV( @PathVariable("jobId") String jobId) {
        IDRBindJob job = jobManager.getSavedJob( jobId );

        // test for not null and complete
        if ( job != null && job.isComplete() && !job.isFailed() ) {
            try {
                IDRBindJobResult result = job.getFuture().get( 1, TimeUnit.SECONDS );

                return ResponseEntity.ok()
                        .contentType( MediaType.parseMediaType("application/octet-stream"))
                        .header( HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + job.getLabel() + "-result.csv\"")
                        .body(result.getResultCSV());

            } catch ( InterruptedException | ExecutionException | TimeoutException e ) {
                log.warn( e );
                ResponseEntity.status( 500 ).body( "" );
            }
        }
        return ResponseEntity.badRequest().body( "" );
    }


}
