package org.openhab.binding.dsmr.device;

import java.util.concurrent.TimeUnit;

/**
 * This class holds all the constants that are applicable for the DSMR Device
 *
 * @author M. Volaart
 * @since 2.0.0
 */
public class DSMRDeviceConstants {
    // State definitions
    public enum DeviceState {
        INITIALIZING,
        STARTING,
        SWITCH_PORT_SPEED,
        ONLINE,
        OFFLINE,
        SHUTDOWN,
        CONFIGURATION_PROBLEM;
    }

    public enum DSMRPortEvent {
        CLOSED("Serial port closed"),
        OPENED("Serial port opened"),
        READ_OK("Read ok"),
        READ_ERROR("Read error"),
        LINE_BROKEN("Serial line is broken (cable problem?)"),
        CONFIGURATION_ERROR("Configuration error"),
        DONT_EXISTS("Serial port does not exist"),
        IN_USE("Serial port is already in use"),
        NOT_COMPATIBLE("Serial port is not compatible"),
        WRONG_BAUDRATE("Wrong baudrate"),
        ERROR("General error");

        public final String eventDetails;

        DSMRPortEvent(String eventDetails) {
            this.eventDetails = eventDetails;
        }
    }

    public enum DSMRDeviceEvent {
        INITIALIZE("Initializing DSMR device"),
        INITIALIZE_OK("Initialize DSMR device successfull"),
        SWITCH_BAUDRATE("DSMR port switch baudrate"),
        TELEGRAM_RECEIVED("DSMR device received P1 telegram successfull"),
        CONFIGURATION_ERROR("DSMR device has a configuration error"),
        ERROR("DSMR device experienced a general error"),
        READ_ERROR("DSMR port read error"),
        SHUTDOWN("DSMR device shutdown");

        public String eventDetails;

        DSMRDeviceEvent(String eventDetails) {
            this.eventDetails = eventDetails;
        }
    }

    // Serial port read time out (15 seconds)
    public static final int SERIAL_PORT_READ_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(15);
    // Timeout for detecting the correct serial port settings
    public static final int SERIAL_PORT_AUTO_DETECT_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(30);
    // // Timeout for recovery from offline mode
    public static final int RECOVERY_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(30);
}
