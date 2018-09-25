package com.jacobsonmt.idrbind.rest;

import com.jacobsonmt.idrbind.model.IDRBindJob;
import com.jacobsonmt.idrbind.services.JobManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@Log4j2
@RequestMapping("/api")
@RestController
public class JobEndpoint {

    @Autowired
    private JobManager jobManager;

    @RequestMapping(value = "/job/{jobId}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    public IDRBindJob.IDRBindJobVO getJob(@PathVariable String jobId) {
        return createJobValueObject( jobManager.getSavedJob( jobId ) );
    }

    @RequestMapping(value = "/job", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    public IDRBindJob.IDRBindJobVO getJob2(@RequestParam(value = "jobId") String jobId) {
        return createJobValueObject( jobManager.getSavedJob( jobId ) );
    }

    @RequestMapping(value = "/job/{jobId}/status", method = RequestMethod.GET, produces = {MediaType.TEXT_PLAIN_VALUE})
    public String getJobStatus(@PathVariable String jobId) {
        IDRBindJob job = jobManager.getSavedJob( jobId );
        if ( job != null ) {
            return job.getStatus();
        }

        log.info( "Job Not Found" );
        return "Job Not Found";
    }

    @RequestMapping(value = "/job/status", method = RequestMethod.GET, produces = {MediaType.TEXT_PLAIN_VALUE})
    public String getJobStatus2(@RequestParam(value = "jobId") String jobId) {
        IDRBindJob job = jobManager.getSavedJob( jobId );
        if ( job != null ) {
            return job.getStatus();
        }

        log.info( "Job Not Found" );
        return "Job Not Found";
    }

    @RequestMapping(value = "/submitJob", method = RequestMethod.GET, produces = {MediaType.TEXT_PLAIN_VALUE})
    public String submitJob(@RequestParam(value = "label") String label,
                                @RequestParam(value = "pdbContent") String pdbContent,
                                @RequestParam(value = "proteinChain") String proteinChain,
                                @RequestParam(value = "email", required = false, defaultValue = "") String email,
                                @RequestParam(value = "hidden", required = false, defaultValue = "false") boolean hidden,
                                HttpServletRequest request
                                ) {
        String ipAddress = request.getHeader( "X-FORWARDED-FOR" );
        if ( ipAddress == null ) {
            ipAddress = request.getRemoteAddr();
        }

        IDRBindJob job = jobManager.createJob( label, label, pdbContent, proteinChain, email, hidden );
        jobManager.submit( job );
        log.info( "Job Submitted: " + job.getJobId() );
        return "Job Submitted: " + job.getJobId();
    }

    private IDRBindJob.IDRBindJobVO createJobValueObject( IDRBindJob job) {
        if ( job == null ) {
            return null;
        }
        return job.toValueObject(true);
    }


}
