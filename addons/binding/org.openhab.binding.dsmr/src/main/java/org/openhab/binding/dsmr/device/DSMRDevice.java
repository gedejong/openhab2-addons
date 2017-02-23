package org.openhab.binding.dsmr.device;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

import org.openhab.binding.dsmr.DSMRWatchdogHelper;
import org.openhab.binding.dsmr.DSMRWatchdogListener;
import org.openhab.binding.dsmr.device.DSMRDeviceConstants.DSMRDeviceEvent;
import org.openhab.binding.dsmr.device.DSMRDeviceConstants.DSMRPortEvent;
import org.openhab.binding.dsmr.device.DSMRDeviceConstants.DeviceState;
import org.openhab.binding.dsmr.device.cosem.CosemObject;
import org.openhab.binding.dsmr.device.p1telegram.P1TelegramListener;
import org.openhab.binding.dsmr.device.p1telegram.P1TelegramParser;
import org.openhab.binding.dsmr.discovery.DSMRMeterDetector;
import org.openhab.binding.dsmr.discovery.DSMRMeterDiscoveryListener;
import org.openhab.binding.dsmr.meter.DSMRMeter;
import org.openhab.binding.dsmr.meter.DSMRMeterDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The DSMRDevice class represents the physical Thing within OpenHAB2 context
 *
 * The DSMRDevice will always starts in the state INITIALIZING meaning the port
 * is setup and OS resources are claimed.
 *
 * The device is waiting in the state STARTING till the data stream from the serial port can be parsed successfully.
 * Then the device will enter the state ONLINE.
 *
 * If there are problems reading the port and no CosemObjects are received anymore, the device will enter the state
 * OFFLINE.
 *
 * If the OpenHAB2 system wants the binding to shutdown, the DSMRDevice enters the SHUTDOWN state and will close
 * and release the OS resources.
 *
 * In case of configuration errors the DSMRDevice will enter the state CONFIGURATION_PROBLEM
 *
 * Please note that these are DSMRDevice specific states and will be mapped on the OpenHAB2 states
 *
 * @author M. Volaart
 * @since 2.0.0
 */
public class DSMRDevice implements P1TelegramListener, DSMRPortEventListener, DSMRWatchdogListener {
    // Logger
    private final Logger logger = LoggerFactory.getLogger(DSMRDevice.class);

    // DSMR Device configuration
    private DSMRDeviceConfiguration deviceConfiguration = null;

    // Device status
    private class DeviceStatus {
        private DeviceState deviceState;
        private long stateEnteredTS;
        private long lastTelegramReceivedTS;
        private List<CosemObject> receivedCosemObjects;
    }

    private final DeviceStatus deviceStatus;

    // DSMR Port
    private DSMRPort dsmrPort = null;

    // P1 TelegramParser Instance
    private P1TelegramParser p1Parser = null;

    // List of available meters
    private List<DSMRMeter> availableMeters = null;

    // listener for discovery of new meter
    private DSMRMeterDiscoveryListener discoveryListener;
    // listener for changes of the DSMR Device state
    private DSMRDeviceStateListener deviceStateListener;

    // Executor for delayed tasks
    private ExecutorService executor;

    // Watchdog
    private ScheduledFuture<?> watchdog;

    /**
     * Creates a new DSMRDevice.
     *
     * The constructor will initialize the state to INITIALIZING.
     *
     * @param deviceConfiguration {@link DSMRDeviceConfiguration} containing the configuration of the DSMR Device
     * @param deviceStateListener {@link DSMRDeviceStateListener} listener that will be notified in case of state
     *            changes of the DSMR device
     * @param discoveryListener {@link DSMRMeterDiscoveryListener} listener that will be notified in case a new
     *            meter is detected
     */
    public DSMRDevice(DSMRDeviceConfiguration deviceConfiguration, DSMRDeviceStateListener deviceStateListener,
            DSMRMeterDiscoveryListener discoveryListener) {
        this.deviceConfiguration = deviceConfiguration;
        this.discoveryListener = discoveryListener;
        this.deviceStateListener = deviceStateListener;
        this.availableMeters = new LinkedList<>();

        deviceStatus = new DeviceStatus();
        deviceStatus.receivedCosemObjects = new ArrayList<CosemObject>();
    }

    /**
     * Sets a new device state
     * If the device has the state SHUTDOWN the new state will not be accepted.
     * This method will also notify the device state listener about the state update (always) / change (only if new
     * state differs from the old state)
     *
     * @param newDeviceState the requested new device state
     * @param stateDetail the details about the new device state
     */
    private void handleDSMRDeviceEvent(DSMRDeviceEvent event, String eventDetails) {
        synchronized (deviceStatus) {
            DeviceState currentDeviceState = deviceStatus.deviceState;
            DeviceState newDeviceState = currentDeviceState;

            logger.debug("Handle DSMRDeviceEvent {} in state {}", event, currentDeviceState);

            if (currentDeviceState != DeviceState.SHUTDOWN && currentDeviceState != DeviceState.CONFIGURATION_PROBLEM) {
                switch (event) {
                    case CONFIGURATION_ERROR:
                        newDeviceState = DeviceState.CONFIGURATION_PROBLEM;
                        break;
                    case ERROR:
                        newDeviceState = DeviceState.OFFLINE;
                        break;
                    case INITIALIZE_OK:
                        if (currentDeviceState == DeviceState.INITIALIZING) {
                            newDeviceState = DeviceState.STARTING;
                        } else {
                            logger.info("Ignoring event {} in state {}", event, currentDeviceState);
                        }
                        break;
                    case READ_ERROR:
                        if (currentDeviceState == DeviceState.ONLINE) {
                            newDeviceState = DeviceState.OFFLINE;
                        } else if (currentDeviceState == DeviceState.STARTING) {
                            newDeviceState = DeviceState.SWITCH_PORT_SPEED;
                        } else if (currentDeviceState != DeviceState.OFFLINE) {
                            logger.debug("Ignore event {} in state {}", event, currentDeviceState);
                        }
                        break;
                    case SHUTDOWN:
                        newDeviceState = DeviceState.SHUTDOWN;
                        break;
                    case INITIALIZE:
                        newDeviceState = DeviceState.INITIALIZING;
                        break;
                    case TELEGRAM_RECEIVED:
                        if (currentDeviceState == DeviceState.OFFLINE || currentDeviceState == DeviceState.STARTING) {
                            newDeviceState = DeviceState.ONLINE;
                        } else if (currentDeviceState != DeviceState.ONLINE) {
                            logger.debug("Cannot make transition from {} to ONLINE", currentDeviceState);
                        }
                        break;
                    case SWITCH_BAUDRATE:
                        if (currentDeviceState == DeviceState.ONLINE) {
                            newDeviceState = DeviceState.OFFLINE;
                        } else if (currentDeviceState == DeviceState.STARTING) {
                            newDeviceState = DeviceState.SWITCH_PORT_SPEED;
                        } else {
                            logger.debug("Ignore event {} in state {}", event, currentDeviceState);
                        }
                        break;
                    default:
                        logger.warn("Unknown event {}", event);

                        break;
                }

                deviceStatus.deviceState = newDeviceState;
                deviceStatus.stateEnteredTS = System.currentTimeMillis();

                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        handleDeviceState();
                    }
                });

                // Notify listeners
                if (deviceStateListener != null) {
                    deviceStateListener.stateUpdated(currentDeviceState, newDeviceState, eventDetails);
                    if (currentDeviceState != newDeviceState) {
                        deviceStateListener.stateChanged(currentDeviceState, newDeviceState, eventDetails);
                    }
                } else {
                    logger.error("No device state listener available, binding will not work properly!");
                }
            } else {
                logger.debug("Setting state is not allowed while in {}", deviceStatus.deviceState);
            }
        }
    }

    private void handleDeviceState() {
        synchronized (deviceStatus) {
            logger.debug("Current DSMRDevice state {}", deviceStatus.deviceState);

            switch (deviceStatus.deviceState) {
                case CONFIGURATION_PROBLEM:
                    /*
                     * Device has a configuration problem. Do nothing, user must change the configuration.
                     * This will trigger an event in the OH2 framework that will finally lead to reinitializing the
                     * DSMRDevice with a new configuration
                     */
                    break;
                case INITIALIZING:
                    handleInitializeDSMRDevice();
                    break;
                case OFFLINE:
                    if (System.currentTimeMillis()
                            - deviceStatus.stateEnteredTS > DSMRDeviceConstants.RECOVERY_TIMEOUT) {
                        logger.info("In offline mode for at least {} ms, reinitialize device",
                                DSMRDeviceConstants.RECOVERY_TIMEOUT);
                        /* Device is still in OFFLINE, try to recover by entering INITIALIZING */
                        handleDSMRDeviceEvent(DSMRDeviceEvent.INITIALIZE, "In offline mode for too long, recovering");
                    }

                    break;
                case ONLINE:
                    if (deviceStatus.receivedCosemObjects.size() > 0) {
                        List<CosemObject> cosemObjects = new ArrayList<CosemObject>();
                        cosemObjects.addAll(deviceStatus.receivedCosemObjects);

                        // Handle cosem objects asynchronous
                        executor.submit(new Runnable() {
                            @Override
                            public void run() {
                                logger.debug("Processing {} cosem objects", cosemObjects);
                                sendCosemObjects(cosemObjects);
                            }
                        });

                        deviceStatus.receivedCosemObjects.clear();
                    } else if (System.currentTimeMillis()
                            - deviceStatus.lastTelegramReceivedTS > DSMRDeviceConstants.RECOVERY_TIMEOUT) {
                        logger.info("No messages received for at least {} ms, reinitialize device",
                                DSMRDeviceConstants.RECOVERY_TIMEOUT);
                        /* Device did not receive telegrams for too long, to to recover by entering INITIALIZING */
                        handleDSMRDeviceEvent(DSMRDeviceEvent.ERROR, "No telegrams received for too long");
                    } else {
                        logger.debug("No Cosem objects to handle");
                    }
                    break;
                case SHUTDOWN:
                    /* Shutdown device */
                    handleShutdownDSMRDevice();
                    break;
                case STARTING:
                    if (System.currentTimeMillis()
                            - deviceStatus.stateEnteredTS > DSMRDeviceConstants.SERIAL_PORT_AUTO_DETECT_TIMEOUT) {
                        /* Device did not receive telegrams for too long, switch port speed */
                        handleDSMRDeviceEvent(DSMRDeviceEvent.SWITCH_BAUDRATE,
                                DSMRDeviceEvent.SWITCH_BAUDRATE.eventDetails);
                    }
                    break;
                case SWITCH_PORT_SPEED:
                    // Switch port speed settings
                    dsmrPort.switchPortSpeed();

                    // Close current port (this will trigger a initialize event)
                    dsmrPort.close();

                    break;
                default:
                    logger.warn("Unknown state {}", deviceStatus.deviceState);

                    break;
            }
            if (deviceStatus.receivedCosemObjects.size() > 0) {
                logger.debug("Dropping {} Cosem objects due to state {}", deviceStatus.receivedCosemObjects.size(),
                        deviceStatus.deviceState);
            }
            deviceStatus.receivedCosemObjects.clear();
        }
    }

    /**
     * These are the cosemObjects that must be send to the available meters
     * This method will iterate through available meters and notify them about
     * the CosemObjects received.
     *
     * @param cosemObjects. The list of {@link CosemObjects} received.
     */
    private void sendCosemObjects(List<CosemObject> cosemObjects) {
        for (DSMRMeter meter : availableMeters) {
            logger.debug("Processing CosemObjects for meter {}", meter);
            List<CosemObject> processedCosemObjects = meter.handleCosemObjects(cosemObjects);
            logger.debug("Processed cosemObjects {}", processedCosemObjects);
            cosemObjects.removeAll(processedCosemObjects);
        }

        if (cosemObjects.size() > 0) {
            logger.info("There are unhandled CosemObjects, start autodetecting meters");

            List<DSMRMeterDescriptor> detectedMeters = DSMRMeterDetector.detectMeters(cosemObjects);
            logger.info("Detected the following new meters: {}", detectedMeters.toString());

            if (discoveryListener != null) {
                for (DSMRMeterDescriptor meterDescriptor : detectedMeters) {
                    if (discoveryListener.meterDiscovered(meterDescriptor)) {
                    } else {
                        logger.info("DiscoveryListener {} rejected meter descriptor {}", discoveryListener,
                                meterDescriptor);
                    }
                }
            } else {
                logger.warn("There is no listener for new meters!");
            }
        }
    }

    /**
     * Initialize the DSMR Device
     */
    private void handleInitializeDSMRDevice() {
        if (dsmrPort != null) {
            dsmrPort.close();
            dsmrPort = null;
        }
        /*
         * Start the parser in lenient mode to prevent flooding logs with errors during initialization
         * of the device
         */
        p1Parser = new P1TelegramParser(true, this);
        dsmrPort = new DSMRPort(deviceConfiguration.serialPort, p1Parser, this,
                DSMRPortSettings.getPortSettingsFromString(deviceConfiguration.serialPortSettings), true);
        // Open the DSMR Port
        dsmrPort.open();
    }

    /**
     * Handle the shutdown of the DSMR device
     */
    private void handleShutdownDSMRDevice() {
        if (dsmrPort != null) {
            dsmrPort.close();
            dsmrPort = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (watchdog != null) {
            watchdog.cancel(false);
        }
    }

    /**
     * Starts the DSMR device
     */
    public void startDevice() {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (watchdog != null) {
            watchdog.cancel(false);
        }
        watchdog = DSMRWatchdogHelper.getInstance().getDSMRWatchdog(this, DSMRDeviceConstants.RECOVERY_TIMEOUT);

        // Reserve 2 threads (1 for handling device state asynchronous and 1 to handle cosem objects asynchronous
        executor = Executors.newFixedThreadPool(2);

        logger.debug("Starting device and entering INITIALIZING state");
        handleDSMRDeviceEvent(DSMRDeviceEvent.INITIALIZE, DSMRDeviceEvent.INITIALIZE.eventDetails);
    }

    /**
     * Stops the DSMR device
     */
    public void stopDevice() {
        handleDSMRDeviceEvent(DSMRDeviceEvent.SHUTDOWN, DSMRDeviceEvent.SHUTDOWN.eventDetails);
    }

    /**
     * Handler for cosemObjects received in a P1 telegram
     *
     * @param cosemObjects. List of received {@link CosemObject} objects
     * @param telegramState. {@link TelegramState} describing the state of the received telegram.
     */
    @Override
    public void telegramReceived(List<CosemObject> cosemObjects, TelegramState telegramState) {
        logger.debug("Received {} Cosem Objects, telegramState: {}", cosemObjects.size(), telegramState);

        synchronized (deviceStatus) {
            // Telegram received, update timestamp
            deviceStatus.lastTelegramReceivedTS = System.currentTimeMillis();

            if (telegramState == TelegramState.OK) {
                if (cosemObjects.size() > 0) {
                    deviceStatus.receivedCosemObjects.addAll(cosemObjects);
                } else {
                    logger.info("Parsing was succesful, however there were no CosemObjects");
                }
                handleDSMRDeviceEvent(DSMRDeviceEvent.TELEGRAM_RECEIVED, telegramState.stateDetails);
            } else {
                if (deviceConfiguration.lenientMode) {
                    // In lenient mode, still send Cosem Objects
                    if (cosemObjects.size() == 0) {
                        logger.warn("Did not receive anything at all in lenient mode");

                        handleDSMRDeviceEvent(DSMRDeviceEvent.ERROR, telegramState.stateDetails);
                    } else {
                        logger.debug("Still handling CosemObjects in lenient mode");
                        deviceStatus.receivedCosemObjects.addAll(cosemObjects);

                        handleDSMRDeviceEvent(DSMRDeviceEvent.TELEGRAM_RECEIVED, telegramState.stateDetails);
                    }
                } else {
                    // Parsing was incomplete, don't send CosemObjects
                    logger.warn("Dropping {} CosemObjects due to incorrect parsing", cosemObjects.size());
                    handleDSMRDeviceEvent(DSMRDeviceEvent.ERROR, telegramState.stateDetails);
                }
            }
        }
    }

    /**
     * Add a supported {@link DSMRMeter}
     *
     * @param dsmrMeter the {@link DSMRMeter} that is supported and can handle {@link CosemObject}
     */
    public void addDSMRMeter(DSMRMeter dsmrMeter) {
        availableMeters.add(dsmrMeter);
    }

    /**
     * Removes a supported {@link DSMRMeter}
     *
     * @param dsmrMeter the {@link DSMRMeter} that won't be supported and doesn't handle {@link CosemObject} anymore.
     */
    public void removeDSMRMeter(DSMRMeter dsmrMeter) {
        availableMeters.remove(dsmrMeter);
    }

    /**
     * Handles port events and sets the device state accordingly
     *
     * NOTE: This method must act on the DSMRDevice instance. This is done in the method setDSMRDeviceState which
     * also handle concurrency
     */
    @Override
    public void handleDSMRPortEvent(DSMRPortEvent portEvent) {
        logger.debug("Handle port event {}", portEvent);

        switch (portEvent) {
            case CLOSED:
                handleDSMRDeviceEvent(DSMRDeviceEvent.INITIALIZE, portEvent.eventDetails);
                break;
            case CONFIGURATION_ERROR:
                handleDSMRDeviceEvent(DSMRDeviceEvent.CONFIGURATION_ERROR, portEvent.eventDetails);
                break;
            case DONT_EXISTS:
                handleDSMRDeviceEvent(DSMRDeviceEvent.CONFIGURATION_ERROR, portEvent.eventDetails);
                break;
            case ERROR:
                handleDSMRDeviceEvent(DSMRDeviceEvent.ERROR, portEvent.eventDetails);
                break;
            case LINE_BROKEN:
                handleDSMRDeviceEvent(DSMRDeviceEvent.READ_ERROR, portEvent.eventDetails);
                break;
            case IN_USE:
                handleDSMRDeviceEvent(DSMRDeviceEvent.ERROR, portEvent.eventDetails);
                break;
            case NOT_COMPATIBLE:
                handleDSMRDeviceEvent(DSMRDeviceEvent.ERROR, portEvent.eventDetails);
                break;
            case OPENED:
                handleDSMRDeviceEvent(DSMRDeviceEvent.INITIALIZE_OK, portEvent.eventDetails);
                break;
            case WRONG_BAUDRATE:
                handleDSMRDeviceEvent(DSMRDeviceEvent.SWITCH_BAUDRATE, portEvent.eventDetails);
                break;
            case READ_ERROR:
                handleDSMRDeviceEvent(DSMRDeviceEvent.READ_ERROR, portEvent.eventDetails);
                break;
            case READ_OK:
                /* Ignore this event */
                break;
            default:
                logger.warn("Unknown DSMR Port event");
                handleDSMRDeviceEvent(DSMRDeviceEvent.ERROR, "Unknown port event");
                break;
        }
    }

    @Override
    /**
     * Keep the DSMR device alive
     * If the binding and the real device is working probably the alive will do nothing
     * since P1 telegrams are received
     */
    public void alive() {
        logger.debug("Alive");
        if (System.currentTimeMillis() - deviceStatus.stateEnteredTS > DSMRDeviceConstants.RECOVERY_TIMEOUT
                && System.currentTimeMillis()
                        - deviceStatus.lastTelegramReceivedTS > DSMRDeviceConstants.RECOVERY_TIMEOUT) {
            logger.debug("Calling handle device state");
            /*
             * No update of state of received telegrams for a period of RECOVERY_TIMEOUT
             * Evaluate current state
             */
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    handleDeviceState();
                }
            });
        }
    }
}
