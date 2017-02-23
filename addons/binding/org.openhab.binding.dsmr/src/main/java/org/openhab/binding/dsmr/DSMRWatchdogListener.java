package org.openhab.binding.dsmr;

/**
 * Listener for and trigger event from the DSMR watchdog
 *
 * @author Marcel Volaart
 * @since 2.1.0
 */
public interface DSMRWatchdogListener {
    /**
     * Watchdog triggers an alive where the implementing class should check the state of its context
     */
    public void alive();
}
