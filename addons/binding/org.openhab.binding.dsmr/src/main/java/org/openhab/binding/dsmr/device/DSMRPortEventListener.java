package org.openhab.binding.dsmr.device;

import org.openhab.binding.dsmr.device.DSMRDeviceConstants.DSMRPortEvent;

/**
 * Interface for handling DSMRPortEvent events
 *
 * @author M. Volaart
 * @since 2.1.0
 */
public interface DSMRPortEventListener {
    /**
     * Handle DSMRPortEvent events
     *
     * @param portEvent @link {@link DSMRPortEvent} that has occurred
     */
    public void handleDSMRPortEvent(DSMRPortEvent portEvent);
}
