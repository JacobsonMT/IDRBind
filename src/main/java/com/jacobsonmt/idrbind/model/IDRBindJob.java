package com.jacobsonmt.idrbind.model;

import com.jacobsonmt.idrbind.services.JobManager;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.StopWatch;

import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.concurrent.*;

@Log4j2
@Getter
@Setter
@Builder
@ToString(of = {"jobId", "userId", "label", "hidden"})
@EqualsAndHashCode(of = {"jobId"})
public class IDRBindJob implements Callable<IDRBindJobResult> {

    // Path to resources
    private String command;
    private Path jobsDirectory;
    private String outputScoredPDBFilename;
    private String outputCSVFilename;

    // Information on creation of job
    private String userId;
    private String jobId;
    private String label;
    private String inputPDBContent;
    private String inputFASTAContent;
    private String inputProteinChainIds;
    @Builder.Default private boolean hidden = true;
    private Date submittedDate;
    private String email;

    // Information on running / completion
    @Builder.Default private boolean running = false;
    @Builder.Default private boolean failed = false;
    @Builder.Default private boolean complete = false;
    private Integer position;
    private String status;

    // Results
    private Future<IDRBindJobResult> future;
    private StreamingOutput resultFile;
    private long executionTime;

    // Saving Job information / results for later
    @Builder.Default private boolean saved = false;
    private Long saveExpiredDate;

    // Back-reference to owning JobManager
    private JobManager jobManager;

    @Override
    public IDRBindJobResult call() throws Exception {

        String resultPDB;
        String resultCSV;

        try {

            log.info( "Starting job (" + label + ") for user: (" + userId + ")" );

            this.running = true;
            this.status = "Processing";
            this.position = 0;

            jobManager.onJobStart( this );

            // Create job directory
            Files.createDirectories( jobsDirectory );

            // Write content to input
            Path pdbFile = jobsDirectory.resolve( "input.pdb" );
            try ( BufferedWriter writer = Files.newBufferedWriter( pdbFile, Charset.forName( "UTF-8" ) ) ) {
                writer.write( inputPDBContent );
            }

            Path fastaFile = jobsDirectory.resolve( "input.fasta" );
            try ( BufferedWriter writer = Files.newBufferedWriter( fastaFile, Charset.forName( "UTF-8" ) ) ) {
                writer.write( inputFASTAContent );
            }

            // Execute script
            StopWatch sw = new StopWatch();
            sw.start();
            String[] commands = {command, "input.pdb", inputProteinChainIds, "input.fasta"};
            executeCommand( commands, jobsDirectory );
            sw.stop();
            this.executionTime = sw.getTotalTimeMillis() / 1000;

            // Get output
            resultPDB = inputStreamToString( Files.newInputStream( jobsDirectory.resolve( outputScoredPDBFilename ) ) );
            resultCSV = inputStreamToString( Files.newInputStream( jobsDirectory.resolve( outputCSVFilename ) ) );

            this.status = "Completed in " + executionTime + "s";
            log.info( "Finished job (" + label + ") for user: (" + userId + ")" );
            this.running = false;
            this.complete = true;

        } catch ( Exception e ) {
            log.error( e );
            this.complete = true;
            this.running = false;
            this.failed = true;
            this.status = "Failed after " + executionTime + "s";
            resultPDB = "";
            resultCSV = "";
        }

        jobManager.onJobComplete( this );
        jobManager = null;
        return new IDRBindJobResult( resultPDB, resultCSV );

    }

    private static String executeCommand( String[] command, Path path ) {

        StringBuffer output = new StringBuffer();

        Process p;
        try {
            p = Runtime.getRuntime().exec( command, null, path.toFile() );
            p.waitFor();
            BufferedReader reader = new BufferedReader( new InputStreamReader( p.getInputStream() ) );

            String line = "";
            while ( ( line = reader.readLine() ) != null ) {
                output.append( line + "\r\n" );
            }

        } catch ( Exception e ) {
            e.printStackTrace();
        }

        return output.toString();
    }

    public static String inputStreamToString(InputStream inputStream) throws IOException {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader
                (inputStream, Charset.forName(StandardCharsets.UTF_8.name())))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        return textBuilder.toString();
    }

    @Getter
    @AllArgsConstructor
    public static final class IDRBindJobVO {
        private final String jobId;
        private final String label;
        private final String status;
        private final boolean running;
        private final boolean failed;
        private final boolean complete;
        private Integer position;
        private final String email;
        private final boolean hidden;
        private final Date submitted;
        private final IDRBindJobResult result;
    }

    public IDRBindJobVO toValueObject(boolean obfuscateEmail) {

        IDRBindJobResult result = null;
        if ( this.isComplete() ) {
            try {
                result = this.getFuture().get( 1, TimeUnit.SECONDS );
            } catch ( InterruptedException | ExecutionException | TimeoutException e ) {
                log.debug( e );
            }
        }

        return new IDRBindJobVO( jobId, label, status, running, failed, complete, position, obfuscateEmail ? email.replaceAll("(\\w{0,3})(\\w+.*)(@.*)", "$1****$3") :  email, hidden, submittedDate, result );
    }

}
