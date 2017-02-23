/**
 * Copyright (c) 2010-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.dsmr.device;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.TooManyListenersException;

import org.openhab.binding.dsmr.device.DSMRDeviceConstants.DSMRPortEvent;
import org.openhab.binding.dsmr.device.p1telegram.P1TelegramParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

/**
 * Class that implements the DSMR port meters that comply to the Dutch Smart Meter Requirements.
 * <p>
 * This class provides a simple public interface: read and close.
 * <p>
 * The read method will claim OS resources if necessary. If the read method
 * encounters problems it will automatically close itself
 * <p>
 * The close method can be called asynchronous and will release OS resources.
 * <p>
 * In this way the DSMR port can restore the connection automatically
 * <p>
 * <code>
 * An example DSMR telegram in accordance to IEC 62056-21 Mode D.<br>
 * /ISk5\2MT382-1000<br>
 * 0-0:96.1.1(4B384547303034303436333935353037)<br>
 * 1-0:1.8.1(12345.678*kWh)<br>
 * 1-0:1.8.2(12345.678*kWh)<br>
 * 1-0:2.8.1(12345.678*kWh)<br>
 * 1-0:2.8.2(12345.678*kWh)<br>
 * 0-0:96.14.0(0002)<br>
 * 1-0:1.7.0(001.19*kW)<br>
 * 1-0:2.7.0(000.00*kW)<br>
 * 0-0:17.0.0(016*A)<br>
 * 0-0:96.3.10(1)<br>
 * 0-0:96.13.1(303132333435363738)<br>
 * 0-0:96.13.0(303132333435363738393A3B3C3D3E3F303132333435363738393A3B3C3D3E3F<br>
 * 303132333435363738393A3B3C3D3E3F303132333435363738393A3B3C3D3E3F<br>
 * 303132333435363738393A3B3C3D3E3F)<br>
 * 0-1:96.1.0(3232323241424344313233343536373839)<br>
 * 0-1:24.1.0(03)<br>
 * 0-1:24.3.0(090212160000)(00)(60)(1)(0-1:24.2.1)(m3)<br>
 * (00000.000)<br>
 * 0-1:24.4.0(1)<br>
 * !<br>
 * </code>
 *
 * @author M. Volaart
 * @since 2.0.0
 */
public class DSMRPort implements SerialPortEventListener {
    /* logger */
    private static final Logger logger = LoggerFactory.getLogger(DSMRPort.class);

    /* private object variables */
    private final String portName;

    /* serial port resources */
    private SerialPort serialPort;
    private BufferedInputStream bis;
    private byte[] buffer = new byte[1024]; // 1K

    /* state variables */
    private DSMRPortSettings portSettings;
    private DSMRPortSettings fixedPortSettings; // Used if DSMR binding has a static port configuration
    private boolean lenientMode = false;

    private class DSMRPortStatus {
        private boolean isOpen = false;
        private boolean bi = false;
        private boolean oe = false;
        private boolean fe = false;
        private boolean pe = false;
    }

    private DSMRPortStatus portStatus;

    /* helpers */
    private P1TelegramParser p1Parser;

    /* listeners */
    private DSMRPortEventListener dsmrPortListener;

    /*
     * The portLock is used for the shared data used when opening and closing
     * the port. The following shared data must be guarded by the lock:
     * SerialPort, BufferedReader
     */
    private Object portLock = new Object();

    /**
     * Creates a new DSMRPort. This is only a reference to a port. The port will
     * not be opened nor it is checked if the DSMR Port can successfully be
     * opened.
     *
     * @param portName
     *            Device identifier of the post (e.g. /dev/ttyUSB0)
     * @param p1Parser
     *            {@link P1TelegramParser}
     * @param fixedPortSettings
     *            {@link PortSettings} object containing fixed port settings. This parameter
     *            may be null. The binding will then use specification default settings
     *            HIGH_SPEED (i.e. 115200 8N1) and LOW_SPEED (9600 7E1) and auto detect which
     *            is applicable.
     *            If the parameter is set, the binding will ONLY use the specified settings
     *            auto detect functionality will only use the specified settings.
     */
    public DSMRPort(String portName, P1TelegramParser p1Parser, DSMRPortEventListener dsmrPortListener,
            DSMRPortSettings fixedPortSettings, boolean lenientMode) {
        this.portName = portName;
        this.p1Parser = p1Parser;
        this.fixedPortSettings = fixedPortSettings;
        this.lenientMode = lenientMode;
        this.dsmrPortListener = dsmrPortListener;

        portStatus = new DSMRPortStatus();

        /*
         * Februari 2017
         * Due to the Dutch Smart Meter program where every residence is provided
         * a smart for free and the meters are DSMR V4 or higher
         * we assume the majority of meters communicate with HIGH_SPEED_SETTINGS
         * For older meters this means initializing is taking probably 1 minute
         */
        portSettings = DSMRPortSettings.HIGH_SPEED_SETTINGS;
    }

    /**
     * Opens the Operation System Serial Port
     * <p>
     * This method opens the port and set Serial Port parameters according to
     * the DSMR specification. Since the specification is clear about these
     * parameters there are not configurable.
     * <p>
     * If there are problem while opening the port, it is the responsibility of
     * the calling method to handle this situation (and for example close the
     * port again).
     * <p>
     * Opening an already open port is harmless. The method will return
     * immediately
     *
     * @return {@link DeviceStateDetail} containing the details about the DeviceState
     */
    public void open() {
        synchronized (portLock) {
            // Sanity check
            if (portStatus.isOpen) {
                logger.debug("Serial Port is already open, keep current port instance");

                dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.OPENED);
            }
            try {
                logger.debug("Opening port {}", portName);

                // Opening Operating System Serial Port
                CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
                CommPort commPort = portIdentifier.open("org.openhab.binding.dsmr",
                        DSMRDeviceConstants.SERIAL_PORT_READ_TIMEOUT);
                serialPort = (SerialPort) commPort;

                // Configure Serial Port based on specified port speed
                logger.debug("Configure serial port parameters: {}", portSettings);

                if (portSettings != null) {
                    serialPort.setSerialPortParams(portSettings.getBaudrate(), portSettings.getDataBits(),
                            portSettings.getStopbits(), portSettings.getParity());

                    /*
                     * special settings for low speed port (checking reference here)
                     *
                     * I don't know why this is necessary (these lines are not connected)
                     */
                    if (portSettings == DSMRPortSettings.LOW_SPEED_SETTINGS) {
                        serialPort.setDTR(false);
                        serialPort.setRTS(true);
                    }

                    // SerialPort is ready, open the reader
                    logger.info("SerialPort opened successful");
                    try {
                        bis = new BufferedInputStream(serialPort.getInputStream());
                    } catch (IOException ioe) {
                        logger.error("Failed to get inputstream for serialPort. Closing port", ioe);

                        dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.ERROR);
                    }
                    logger.info("DSMR Port opened successful");
                    portStatus.isOpen = true;

                    try {
                        serialPort.addEventListener(this);
                    } catch (TooManyListenersException tmle) {
                        logger.error("DSMR binding will not be operational.", tmle);
                    }

                    /*
                     * Let the listener known the port is opened
                     * before we start listening for serial port events
                     */
                    dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.OPENED);

                    /*
                     * Start listening for events
                     */
                    serialPort.notifyOnDataAvailable(true);
                    serialPort.notifyOnBreakInterrupt(true);
                    serialPort.notifyOnFramingError(true);
                    serialPort.notifyOnOverrunError(true);
                    serialPort.notifyOnParityError(true);
                } else {
                    logger.error("Invalid port parameters, closing port:{}", portSettings);

                    dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.CONFIGURATION_ERROR);
                }
            } catch (NoSuchPortException nspe) {
                logger.error("Port {} does not exists", portName, nspe);

                dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.DONT_EXISTS);
            } catch (PortInUseException piue) {
                logger.error("Port already in use: {}", portName, piue);

                dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.IN_USE);
            } catch (UnsupportedCommOperationException ucoe) {
                logger.error(
                        "Port does not support requested port settings " + "(invalid dsmr:portsettings parameter?): {}",
                        portName, ucoe);

                dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.NOT_COMPATIBLE);
            }
        }
    }

    /**
     * Closes the DSMRPort and release OS resources
     *
     * The listener will be notified of the closed event. This event is sent while holding
     * the portLock ensuring correct order of handling events
     */
    public void close() {
        synchronized (portLock) {
            logger.info("Closing DSMR port");

            // Stop listening for serial port events
            if (serialPort != null) {
                serialPort.removeEventListener();
            }

            // Close resources
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException ioe) {
                    logger.debug("Failed to close reader", ioe);
                }
            }
            if (serialPort != null) {
                serialPort.close();
            }

            // Release resources
            bis = null;
            serialPort = null;

            portStatus.isOpen = false;

            dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.CLOSED);
        }
    }

    /**
     * Start reading from the DSMR port.
     *
     * @return {@link DeviceStateDetail} containing the details about the DeviceState
     */
    private DSMRPortEvent read() {
        // open port if it is not open
        if (!portStatus.isOpen) {
            logger.warn("DSMRPort is not open, no values will be read");

            // Close port again (just make sure)
            close();

            return DSMRPortEvent.CLOSED;
        }

        try {
            // Read without block
            int bytesAvailable = bis.available();
            while (bytesAvailable > 0) {
                int bytesRead = bis.read(buffer, 0, Math.min(bytesAvailable, buffer.length));

                if (bytesRead > 0) {
                    p1Parser.parseData(buffer, 0, bytesRead);
                } else {
                    logger.debug("Expected bytes {} to read, but {} bytes were read", bytesAvailable, bytesRead);
                }
                bytesAvailable = bis.available();
            }
            return DSMRPortEvent.READ_OK;
        } catch (IOException ioe) {
            /*
             * Read is interrupted. This can be due to a broken connection or
             * closing the port
             */
            if (!portStatus.isOpen) {
                // Closing on purpose
                logger.info("Read aborted: DSMRPort is closed");

                return DSMRPortEvent.CLOSED;
            } else {
                // Closing due to broken connection

                logger.warn("DSMRPort is not available anymore, closing port");
                logger.debug("Caused by:", ioe);

                close();

                return DSMRPortEvent.READ_ERROR;
            }
        } catch (NullPointerException npe) {
            if (!portStatus.isOpen) {
                // Port was closed
                logger.info("Read aborted: DSMRPort is closed");
            } else {
                logger.error("Unexpected problem occured, closing DMSR Port", npe);

                close();
            }
            return DSMRPortEvent.CLOSED;
        }
    }

    /**
     * Switch the Serial Port speed (LOW --> HIGH and vice versa).
     */
    public void switchPortSpeed() {
        if (fixedPortSettings == null) {
            logger.debug("No fixed port setting (autodetect ENABLED), switch between specification standard settings");

            // Checking instance reference here since these are final
            if (portSettings == DSMRPortSettings.HIGH_SPEED_SETTINGS) {
                portSettings = DSMRPortSettings.LOW_SPEED_SETTINGS;
            } else {
                portSettings = DSMRPortSettings.HIGH_SPEED_SETTINGS;
            }
            logger.debug("Switched port settings to: {}", portSettings);
        } else {
            portSettings = fixedPortSettings;
            logger.info("Fixed port settings configured (autodetect DISABLED): {}", portSettings);
        }
    }

    @Override
    public void serialEvent(SerialPortEvent seEvent) {
        switch (seEvent.getEventType()) {
            case SerialPortEvent.DATA_AVAILABLE:
                if (seEvent.getNewValue()) {
                    /* data is available, clear error flags */
                    portStatus.bi = false;
                    portStatus.oe = false;
                    portStatus.fe = false;
                    portStatus.pe = false;

                    DSMRPortEvent portState = read();
                    logger.trace("Port state after read: {}", portState);
                    dsmrPortListener.handleDSMRPortEvent(portState);
                }
                break;
            case SerialPortEvent.BI:
                if (seEvent.getNewValue()) {
                    if (!portStatus.bi) {
                        logger.info("Serial Communication is broken");
                        portStatus.bi = true;
                        dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.LINE_BROKEN);
                    } // Don't notify again
                } else {
                    portStatus.bi = false;
                    logger.debug("BI is recovered");
                }
                break;
            case SerialPortEvent.FE:
                if (seEvent.getNewValue()) {
                    if (!portStatus.fe) {
                        portStatus.fe = true;

                        if (portStatus.pe) {
                            // Both a frame and parity error --> possible wrong baud rate
                            logger.debug("Experienced both parity and frame error caused possibly by a wrong baudrate");

                            p1Parser.abortTelegram();
                            dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.WRONG_BAUDRATE);
                        } else {
                            // Handle frame error
                            if (lenientMode) {
                                logger.debug("Experienced frame error");
                            } else {
                                logger.warn("Experienced frame error");
                                p1Parser.abortTelegram();
                                dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.READ_ERROR);
                            }
                        }
                    } // Don't notify again
                } else {
                    portStatus.fe = false;
                    logger.debug("FE is recovered");
                }
                break;
            case SerialPortEvent.OE:
                if (seEvent.getNewValue()) {
                    if (!portStatus.oe) {
                        portStatus.oe = true;

                        if (lenientMode) {
                            logger.debug("Experienced overrun error");
                        } else {
                            logger.warn("Experienced overrun error");
                            p1Parser.abortTelegram();
                            dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.READ_ERROR);
                        }
                    } // Don't notify again
                } else {
                    portStatus.oe = false;
                    logger.debug("OE is recovered");
                }
                break;
            case SerialPortEvent.PE:
                if (seEvent.getNewValue()) {
                    if (!portStatus.pe) {
                        portStatus.pe = true;
                        if (portStatus.fe) {
                            // Both a frame and parity error --> possible wrong baud rate
                            logger.debug("Experienced both parity and frame error caused possibly by a wrong baudrate");

                            p1Parser.abortTelegram();
                            dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.WRONG_BAUDRATE);
                        } else {
                            // Handle parity error
                            if (lenientMode) {
                                logger.debug("Experienced parity error");
                            } else {
                                logger.warn("Experienced parity error");
                                p1Parser.abortTelegram();
                                dsmrPortListener.handleDSMRPortEvent(DSMRPortEvent.READ_ERROR);
                            }
                        }
                    } // Don't nofity again
                } else {
                    portStatus.pe = false;
                    logger.debug("PE is recovered");
                }
                break;
            default: /* do nothing */
        }
    }
}
