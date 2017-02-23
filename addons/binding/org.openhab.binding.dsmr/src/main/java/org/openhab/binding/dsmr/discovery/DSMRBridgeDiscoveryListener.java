package org.openhab.binding.dsmr.discovery;

/**
 * This interface is notified of new meter discoveries
 *
 * @author Marcel Volaart
 * @since 2.0.0
 */
public interface DSMRBridgeDiscoveryListener {
    /**
     * A new bridge is discovered
     *
     * @param serialPort serial port identifier (e.g. /dev/ttyUSB0 or COM1)
     *
     * @return true if the new bridge is accepted, false otherwise
     */
    public boolean bridgeDiscovered(String serialPort);
}
