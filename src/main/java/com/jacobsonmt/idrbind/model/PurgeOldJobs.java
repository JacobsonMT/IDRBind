package com.jacobsonmt.idrbind.model;

import lombok.extern.log4j.Log4j2;

import java.util.Iterator;
import java.util.Map;

@Log4j2
public class PurgeOldJobs implements Runnable {

    private Map<String, IDRBindJob> savedJobs;

    public PurgeOldJobs( Map<String, IDRBindJob> savedJobs ) {
        this.savedJobs = savedJobs;
    }

    @Override
    public void run() {
        int jobsPurged = 0;
        synchronized ( savedJobs ) {
            for ( Iterator<Map.Entry<String, IDRBindJob>> it = savedJobs.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, IDRBindJob> entry = it.next();
                IDRBindJob job = entry.getValue();
                if ( job.isComplete() && System.currentTimeMillis() > job.getSaveExpiredDate() ) {
                    job.setSaved( false );
                    job.setSaveExpiredDate( null );
                    it.remove();
                    log.debug( "Purged " + job.getJobId() );
                    jobsPurged++;
                }
            }
        }
        log.info( "Purged " + Integer.toString( jobsPurged ) + " old jobs." );
    }

}