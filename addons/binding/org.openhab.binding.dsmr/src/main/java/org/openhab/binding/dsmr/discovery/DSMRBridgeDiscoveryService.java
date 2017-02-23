package org.openhab.binding.dsmr.discovery;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.dsmr.DSMRBindingConstants;
import org.openhab.binding.dsmr.device.DSMRDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.CommPortIdentifier;

/**
 * This implements the discovery service for detecting new DSMR Meters
 *
 * @author M. Volaart
 * @since 2.0.0
 */
public class DSMRBridgeDiscoveryService extends AbstractDiscoveryService implements DSMRBridgeDiscoveryListener {
    // Logger
    private final Logger logger = LoggerFactory.getLogger(DSMRBridgeDiscoveryService.class);

    /**
     * Constructs a new DSMRDiscoveryService with the specified DSMR Bridge ThingUID
     *
     * @param dsmrBridgeUID ThingUID for the DSMR Bridges
     */
    public DSMRBridgeDiscoveryService() {
        super(Collections.singleton(DSMRBindingConstants.THING_TYPE_DSMR_BRIDGE),
                DSMRBindingConstants.DSMR_DISCOVERY_TIMEOUT, false);
    }

    /**
     * Starts a new discovery scan.
     *
     * Since {@link DSMRDevice} will always notify when a new meter is found
     * nothing specific has to be done except just wait till new data is processed.
     *
     * Since new data is available every 10 seconds (< DSMR V5) or every second (DSMRV5)
     * will we just wait DSMR_DISCOVERY_TIMEOUT ({@link DSMRBindingConstants}
     */
    @Override
    protected void startScan() {
        logger.debug("Started Discovery Scan");

        @SuppressWarnings("unchecked")
        Enumeration<CommPortIdentifier> portEnum = CommPortIdentifier.getPortIdentifiers();

        // Traverse each available serial port
        while (portEnum.hasMoreElements()) {
            CommPortIdentifier portIdentifier = portEnum.nextElement();

            // Check only available SERIAL ports
            if (portIdentifier.getPortType() == CommPortIdentifier.PORT_SERIAL && !portIdentifier.isCurrentlyOwned()) {
                logger.debug("Start discovery for serial port: {}", portIdentifier.getName());
                DSMRBridgeDiscoveryHelper discoveryHelper = new DSMRBridgeDiscoveryHelper(portIdentifier.getName(),
                        this);
                discoveryHelper.startDiscovery();
            }
        }
    }

    /**
     * Callback when a new bridge is discovered.
     * At this moment there is no reason why a bridge is not accepted.
     *
     * Therefore this method will always return true
     *
     * @param serialPort the serialPort name of the new discovered DSMRBridge Thing
     * @return true if bridge is accepted, false otherwise
     */
    @Override
    public boolean bridgeDiscovered(String serialPort) {
        ThingUID thingUID = new ThingUID(DSMRBindingConstants.THING_TYPE_DSMR_BRIDGE,
                Integer.toHexString(serialPort.hashCode()));

        // Construct the configuration for this meter
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("serialPort", serialPort);

        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID)
                .withThingType(DSMRBindingConstants.THING_TYPE_DSMR_BRIDGE).withProperties(properties)
                .withLabel("DSMR bridge on " + serialPort).build();

        logger.debug("{} for serialPort {}", discoveryResult, serialPort);

        thingDiscovered(discoveryResult);

        return true;
    }
}