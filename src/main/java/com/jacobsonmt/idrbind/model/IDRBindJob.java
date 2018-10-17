package com.jacobsonmt.idrbind.model;

import com.jacobsonmt.idrbind.services.JobManager;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.StopWatch;

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
public class IDRBindJob implements Callable<IDRBindJobResult>, Serializable {

    private static final long serialVersionUID = 1L;

    // Path to resources
    private transient String command;
    private transient Path jobsDirectory;
    private String outputScoredPDBFilename;
    private String outputCSVFilename;
    private String inputPDBFilename;
    private String inputFASTAFilename;
    private String jobSerializationFilename;

    // Information on creation of job
    private String userId;
    private String jobId;
    private String label;
    private transient String inputPDBContent;
    private transient String inputFASTAContent;
    private String inputProteinChainIds;
    @Builder.Default private boolean hidden = true;
    private Date submittedDate;
    private Date startedDate;
    private Date finishedDate;
    private transient String email;

    // Information on running / completion
    @Builder.Default private boolean running = false;
    @Builder.Default private boolean failed = false;
    @Builder.Default private boolean complete = false;
    private transient Integer position;
    private String status;

    // Results
    private transient IDRBindJobResult result;
    private long executionTime;

    // Saving Job information / results for later
    @Builder.Default private transient boolean saved = false;
    private transient Long saveExpiredDate;

    // Back-reference to owning JobManager
    private transient JobManager jobManager;

    @Override
    public IDRBindJobResult call() throws Exception {

        try {

            log.info( "Starting job (" + label + ") for user: (" + userId + ")" );

            this.running = true;
            this.status = "Processing";
            this.position = 0;
            this.startedDate =  new Date();

            jobManager.onJobStart( this );

            // Create job directory
            Files.createDirectories( jobsDirectory );

            // Write content to input
            Path pdbFile = jobsDirectory.resolve( inputPDBFilename );
            try ( BufferedWriter writer = Files.newBufferedWriter( pdbFile, Charset.forName( "UTF-8" ) ) ) {
                writer.write( inputPDBContent );
            }

            Path fastaFile = jobsDirectory.resolve( inputFASTAFilename );
            try ( BufferedWriter writer = Files.newBufferedWriter( fastaFile, Charset.forName( "UTF-8" ) ) ) {
                writer.write( inputFASTAContent );
            }

            // Execute script
            StopWatch sw = new StopWatch();
            sw.start();
            String[] commands = {command, inputPDBFilename, inputProteinChainIds, inputFASTAFilename};
            executeCommand( commands, jobsDirectory );
            sw.stop();
            this.executionTime = sw.getTotalTimeMillis() / 1000;
            this.finishedDate =  new Date();

            // Get output
            String resultPDB = inputStreamToString( Files.newInputStream( jobsDirectory.resolve( outputScoredPDBFilename ) ) );
            String resultCSV = inputStreamToString( Files.newInputStream( jobsDirectory.resolve( outputCSVFilename ) ) );

            this.result = new IDRBindJobResult( resultPDB, resultCSV );
            this.status = "Completed in " + executionTime + "s";
            log.info( "Finished job (" + label + ") for user: (" + userId + ")" );
            this.running = false;
            this.complete = true;

            // Write metadata to job folder
            Path serializedJob = jobsDirectory.resolve( jobSerializationFilename );
            try ( ObjectOutputStream oos = new ObjectOutputStream( Files.newOutputStream( serializedJob ) ) ) {
                oos.writeObject( this );
            }

        } catch ( Exception e ) {
            log.error( e );
            this.finishedDate =  new Date();
            this.result = new IDRBindJobResult( "", "" );
            this.complete = true;
            this.running = false;
            this.failed = true;
            this.status = "Failed after " + executionTime + "s";
        }


        jobManager.onJobComplete( this );
        jobManager = null;
        return this.result;

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
        private final Integer position;
        private final String email;
        private final boolean hidden;
        private final Date submittedDate;
        private final Date startedDate;
        private final Date finishedDate;
        private final String inputProteinChainIds;
        private final IDRBindJobResult result;
    }

    public IDRBindJobVO toValueObject(boolean obfuscateEmail) {
        return new IDRBindJobVO( jobId, label, status, running, failed, complete, position,
                obfuscateEmail ? email.replaceAll("(\\w{0,3})(\\w+.*)(@.*)", "$1****$3") :  email,
                hidden, submittedDate, startedDate, finishedDate, inputProteinChainIds, result );
    }

}
