package com.jacobsonmt.idrbind.services;

import com.jacobsonmt.idrbind.model.IDRBindJob;
import com.jacobsonmt.idrbind.model.IDRBindJobResult;
import com.jacobsonmt.idrbind.model.PurgeOldJobs;
import com.jacobsonmt.idrbind.settings.ApplicationSettings;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.mail.MessagingException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
@Service
public class JobManager {

    @Autowired
    ApplicationSettings applicationSettings;

    @Autowired
    EmailService emailService;

    // Main executor to process jobs
    private ExecutorService executor;

    // Contains a copy of the processing queue of jobs internal to executor.
    // It is non-trivial to extract a list of running/waiting jobs in the executor
    // so we maintain a copy in sync with the real thing.
    private List<IDRBindJob> jobQueueMirror = new LinkedList<>();

    // Secondary user queues or waiting lines. One specific to each user/session.
    private Map<String, Queue<IDRBindJob>> userQueues = new ConcurrentHashMap<>();

    // Contains map of token to saved job for future viewing
    private Map<String, IDRBindJob> savedJobs = new ConcurrentHashMap<>();

    // Used to periodically purge the old saved jobs
    private ScheduledExecutorService scheduler;

    @PostConstruct
    private void initialize() {
        executor = Executors.newSingleThreadExecutor();
        if ( applicationSettings.isPurgeSavedJobs() ) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
            // Checks every hour for old jobs
            scheduler.scheduleAtFixedRate( new PurgeOldJobs( savedJobs ), 0,
                    applicationSettings.getPurgeSavedJobsTimeHours(), TimeUnit.HOURS );
        }

    }

    @PreDestroy
    public void destroy() {
        log.info( "JobManager destroyed" );
        executor.shutdownNow();
        scheduler.shutdownNow();
    }

    public IDRBindJob createJob( String userId,
                                 String label,
                                 String inputPDBContent,
                                 String inputProteinChainIds,
                                 String email,
                                 boolean hidden ) {
        IDRBindJob.IDRBindJobBuilder jobBuilder = IDRBindJob.builder();

        // Static Resources
        jobBuilder.command( applicationSettings.getCommand() );
        jobBuilder.commandWorkingDirectory( applicationSettings.getCommandWorkingDirectory() );
        jobBuilder.inputPDBFullPath( applicationSettings.getInputPDBPath() );
        jobBuilder.inputProteinChainFullPath( applicationSettings.getInputChainPath() );
        jobBuilder.outputScoredPDBFullPath( applicationSettings.getOutputScoredPDBPath() );
        jobBuilder.outputCSVFullPath( applicationSettings.getOutputCSVPath() );

        // Generated
        jobBuilder.jobId( UUID.randomUUID().toString() );

        // User Inputs
        jobBuilder.userId( userId );
        jobBuilder.label( label );
        jobBuilder.inputPDBContent( inputPDBContent );
        jobBuilder.inputProteinChainIds( inputProteinChainIds );
        jobBuilder.hidden( hidden );
        jobBuilder.email( email );

        IDRBindJob job = jobBuilder.build();

        boolean validation = validateJob( job );

        if ( !validation ) {
            job.setComplete( true );
            job.setFailed( true );
            job.setStatus( "Failed" );
        }

        return job;

    }

    private void submitToProcessQueue( IDRBindJob job ) {
        synchronized ( jobQueueMirror ) {
            log.info( "Submitting job (" + job.getJobId() + ") for user: (" + job.getUserId() + ") to process queue" );
            job.setJobManager( this );
            Future<IDRBindJobResult> future = executor.submit( job );
            job.setFuture( future );
            jobQueueMirror.add( job );
            job.setStatus( "Position: " + Integer.toString( jobQueueMirror.size() ) );
            job.setPosition( jobQueueMirror.size() );
        }
    }

    private void submitToUserQueue( IDRBindJob job ) {
        log.info( "Submitting job (" + job.getJobId() + ") for user: (" + job.getUserId() + ") to user queue" );

        Queue<IDRBindJob> jobs = userQueues.computeIfAbsent( job.getUserId(), k -> new LinkedList<>() );

        if ( jobs.size() > applicationSettings.getUserJobLimit() ) {
            log.info( "Too many jobs (" + job.getJobId() + ") for user: (" + job.getUserId() + ")");
            return;
        }

        synchronized ( jobs ) {

            if ( !jobs.contains( job ) ) {
                jobs.add( job );
                job.setStatus( "Pending" );
                saveJob( job );
                submitJobFromUserQueue( job.getUserId() );
            }
        }
    }

    private void submitJobFromUserQueue( String userId ) {
        int cnt = 0;
        synchronized ( jobQueueMirror ) {

            for ( IDRBindJob job : jobQueueMirror ) {
                if ( job.getUserId().equals( userId ) ) cnt++;
            }
        }

        if ( cnt < applicationSettings.getUserProcessLimit() ) {

            Queue<IDRBindJob> jobs = userQueues.get( userId );

            if ( jobs != null ) {
                IDRBindJob job;
                synchronized ( jobs ) {
                    job = jobs.poll();
                }
                if ( job != null ) {
                    job.setSubmittedDate( new Date() );
                    submitToProcessQueue( job );
                }
            }
        }

    }

    private void updatePositions( String userId ) {
        synchronized ( jobQueueMirror ) {
            int idx = 1;

            for ( Iterator<IDRBindJob> iterator = jobQueueMirror.iterator(); iterator.hasNext(); ) {
                IDRBindJob job = iterator.next();

                if ( job.isRunning() ) {
                    job.setStatus( "Processing" );
                    idx++;
                } else if ( job.isComplete() ) {
                    job.setStatus( "Completed in " + job.getExecutionTime() + "s" );
                    job.setPosition( null );
                    iterator.remove();
                } else {
                    job.setStatus( "Position: " + Integer.toString( idx ) );
                    job.setPosition( idx );
                    idx++;
                }
            }
        }
    }

    public void submit( IDRBindJob job ) {
        submitToUserQueue( job );
    }

    public IDRBindJob getSavedJob( String jobId ) {
        IDRBindJob job = savedJobs.get( jobId );
        if ( job !=null ) {
            // Reset purge datetime
            job.setSaveExpiredDate( System.currentTimeMillis() + applicationSettings.getPurgeAfterHours() * 60 * 60 * 1000 );
        }
        return job;
    }

    private String saveJob( IDRBindJob job ) {
        synchronized ( savedJobs ) {
            job.setSaved( true );
            savedJobs.put( job.getJobId(), job );
            return job.getJobId();
        }
    }

    private boolean validateJob( IDRBindJob job ) {
        return true;
    }

    public void onJobStart( IDRBindJob job ) {
        if ( applicationSettings.isEmailOnJobStart() && job.getEmail() != null && !job.getEmail().isEmpty() ) {
            try {
                emailService.sendJobStartMessage( job );
            } catch ( MessagingException e ) {
                log.error( e );
            }
        }
    }

    public void onJobComplete( IDRBindJob job ) {
        job.setSaveExpiredDate( System.currentTimeMillis() + applicationSettings.getPurgeAfterHours() * 60 * 60 * 1000 );
        updatePositions( job.getUserId() );
        if ( job.getEmail() != null && !job.getEmail().isEmpty() ) {
            try {
                emailService.sendJobCompletionMessage( job );
            } catch ( MessagingException e ) {
                log.error( e );
            }
        }
        // Add new job for given session
        submitJobFromUserQueue( job.getUserId() );
        log.info( String.format( "Jobs in queue: %d", jobQueueMirror.size() ) );
    }

    public List<IDRBindJob.IDRBindJobVO> listPublicJobs() {
        return Stream.concat(jobQueueMirror.stream(), savedJobs.values().stream())
                .distinct()
                .filter( j -> !j.isHidden() )
                .map( j -> j.toValueObject( true ) )
                .sorted(
                        Comparator.comparing(IDRBindJob.IDRBindJobVO::getPosition, Comparator.nullsLast(Integer::compareTo))
                                .thenComparing(IDRBindJob.IDRBindJobVO::getSubmitted, Comparator.nullsLast(Date::compareTo).reversed())
                                .thenComparing(IDRBindJob.IDRBindJobVO::getStatus, String::compareToIgnoreCase)
                )
                .collect( Collectors.toList() );
    }


}
