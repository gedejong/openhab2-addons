package org.openhab.binding.dsmr;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The DSMRTimer class contains helpers for using timers for the DSMR binding
 *
 * Note that this class is a singleton using the 'Initialization-on-demand holder idiom'
 *
 *
 * @author M. Volaart
 * @since 2.1.0
 */
public class DSMRWatchdogHelper {
    // Logger
    private final Logger logger = LoggerFactory.getLogger(DSMRWatchdogHelper.class);

    // Instance holder
    private static class DSMRWatchdogHelperHolder {
        private static final DSMRWatchdogHelper INSTANCE = new DSMRWatchdogHelper();
    }

    // ScheduledThreadPoolExecutor holding the timers
    private ScheduledThreadPoolExecutor service;

    /**
     * Constructor
     *
     * This constructor does nothing. The DSMRTimer is initialized with startUp
     */
    private DSMRWatchdogHelper() {
    }

    /**
     * Returns an instance to this DSMRTimer
     *
     * @return DSMRTimer
     */
    public static DSMRWatchdogHelper getInstance() {
        return DSMRWatchdogHelperHolder.INSTANCE;
    }

    /**
     * Starts the DSMRTimer. Note that the running ScheduledThreadPoolExecutor is stopped
     * and all running timers will end.
     */
    public void start() {
        // Stop running timer
        stop();

        /*
         * We use only 1 thread. The timers that will be used are running only once per
         * 10 - 30 seconds and will be finish instantly
         */
        service = new ScheduledThreadPoolExecutor(1);
    }

    /**
     * Stops the DSMRTimer. The running ScheduledThreadPoolExecutor is stopped and thus
     * all running timers will end.
     */
    public void stop() {
        if (service != null) {
            service.shutdownNow();

            service = null;
        }
    }

    /**
     * Returns the ScheduledFuture for the specified DSMR meter watchdog task
     *
     * @param task the Runnable to run
     *
     * @return ScheduledFuture for this task
     */
    public ScheduledFuture<?> getDSMRWatchdog(DSMRWatchdogListener listener, int period) {
        if (service != null) {
            return service.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    listener.alive();
                }
            }, period, period, TimeUnit.MILLISECONDS);
        } else {
            logger.warn("Trying to get a DSMRMeterWatchdog while DSMRTimer is stopped");
            return null;
        }
    }
}
