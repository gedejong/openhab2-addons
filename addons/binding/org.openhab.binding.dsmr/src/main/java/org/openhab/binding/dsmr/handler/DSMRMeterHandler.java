/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.dsmr.handler;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.dsmr.internal.device.cosem.CosemDate;
import org.openhab.binding.dsmr.internal.device.cosem.CosemInteger;
import org.openhab.binding.dsmr.internal.device.cosem.CosemObject;
import org.openhab.binding.dsmr.internal.device.cosem.CosemString;
import org.openhab.binding.dsmr.internal.device.cosem.CosemValue;
import org.openhab.binding.dsmr.internal.meter.DSMRMeter;
import org.openhab.binding.dsmr.internal.meter.DSMRMeterConfiguration;
import org.openhab.binding.dsmr.internal.meter.DSMRMeterConstants;
import org.openhab.binding.dsmr.internal.meter.DSMRMeterDescriptor;
import org.openhab.binding.dsmr.internal.meter.DSMRMeterListener;
import org.openhab.binding.dsmr.internal.meter.DSMRMeterType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MeterHandler will create logic DSMR meter ThingTypes
 */
public class DSMRMeterHandler extends BaseThingHandler implements DSMRMeterListener {
    // logger
    private final Logger logger = LoggerFactory.getLogger(DSMRMeterHandler.class);

    private static final DateFormat FAILURE_FORMAT = new SimpleDateFormat("d MMM yyyy HH:mm:ss");

    // The DSMRMeter instance
    private DSMRMeter meter = null;

    // Timestamp when last values were received
    private long lastValuesReceivedTs = 0;

    // Reference to the meter watchdog
    private ScheduledFuture<?> meterWatchdog;

    /**
     * Creates a new MeterHandler for the given Thing
     *
     * @param thing {@link Thing} to create the MeterHandler for
     */
    public DSMRMeterHandler(Thing thing) {
        super(thing);
    }

    /**
     * DSMR Meter don't support handling commands
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // No comments can be handled
    }

    /**
     * Initializes a DSMR Meter
     *
     * This method will load the corresponding configuration
     */
    @Override
    public void initialize() {
        logger.debug("Initialize MeterHandler for Thing {}", getThing().getUID());

        Configuration config = getThing().getConfiguration();
        DSMRMeterConfiguration meterConfig = null;
        DSMRMeterDescriptor meterDescriptor = null;

        if (config != null) {
            meterConfig = config.as(DSMRMeterConfiguration.class);
        } else {
            logger.warn("{} does not have a configuration", getThing());
        }

        if (meterConfig != null) {
            DSMRMeterType meterType = null;

            try {
                meterType = DSMRMeterType.valueOf(getThing().getThingTypeUID().getId().toUpperCase());
            } catch (Exception exception) {
                logger.error("Invalid meterType", exception);
            }

            if (meterType != null) {
                meterDescriptor = new DSMRMeterDescriptor(meterType, meterConfig.channel);
            }
        } else {
            logger.warn("Invalid meter configuration for {}", getThing());
        }
        if (meterDescriptor != null) {
            meter = new DSMRMeter(meterDescriptor, this);

            // Initialize meter watchdog
            meterWatchdog = scheduler.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    if (System.currentTimeMillis()
                            - lastValuesReceivedTs > DSMRMeterConstants.METER_VALUES_RECEIVED_TIMEOUT) {
                        if (!getThing().getStatus().equals(ThingStatus.OFFLINE)) {
                            updateStatus(ThingStatus.OFFLINE);
                        }
                    }
                }
            }, DSMRMeterConstants.METER_VALUES_RECEIVED_TIMEOUT, DSMRMeterConstants.METER_VALUES_RECEIVED_TIMEOUT,
                    TimeUnit.MILLISECONDS);

            updateStatus(ThingStatus.OFFLINE);
        } else {
            logger.warn("{} could not be initialized. Delete this Thing if the problem persists.", getThing());
            updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.CONFIGURATION_ERROR,
                    "This could not be initialized. Delete Thing if the problem persists.");
        }
    }

    /**
     * Disposes this Meter Handler
     */
    @Override
    public void dispose() {
        if (meterWatchdog != null) {
            meterWatchdog.cancel(false);
            meterWatchdog = null;
        }
    }

    /**
     * Remove the Meter Thing
     */
    @Override
    public void handleRemoval() {
        // Stop the timeout timer
        if (meterWatchdog != null) {
            meterWatchdog.cancel(false);
            meterWatchdog = null;
        }
        updateStatus(ThingStatus.REMOVED);
    }

    /**
     * Callback for received meter values
     *
     * In this method the conversion is done from the {@link CosemObjct} to the OpenHAB value.
     * For CosemObjects containing more then one value post processing is needed
     *
     */
    @Override
    @SuppressWarnings("incomplete-switch")
    public void meterValueReceived(CosemObject obj) {
        List<? extends CosemValue<? extends Object>> cosemValues = obj.getCosemValues();

        State newState = null;

        // Update the internal states
        if (cosemValues.size() > 0) {
            lastValuesReceivedTs = System.currentTimeMillis();

            if (!getThing().getStatus().equals(ThingStatus.ONLINE)) {
                updateStatus(ThingStatus.ONLINE);
            }
        }

        if (cosemValues.size() == 1) {
            // Regular CosemObject just send the value
            newState = obj.getCosemValue(0).getOpenHABValue();
        } else if (cosemValues.size() > 1) {
            CosemValue<? extends Object> cosemValue = null;
            // Special CosemObjects need special handling
            switch (obj.getType()) {
                case EMETER_VALUE:
                    cosemValue = obj.getCosemValue(1);
                    break;
                case EMETER_POWER_FAILURE_LOG:
                    // TODO: We now only supports last log entry
                    CosemDate endDate = (CosemDate) obj.getCosemValue(2);
                    CosemInteger duration = (CosemInteger) obj.getCosemValue(3);

                    if (endDate != null && duration != null) {
                        cosemValue = new CosemString("",
                                FAILURE_FORMAT.format(endDate.getValue()) + ", " + duration.getValue() + " seconds");
                    } else {
                        cosemValue = new CosemString("", "No failures");
                    }

                    break;
                case GMETER_24H_DELIVERY_V2:
                    cosemValue = obj.getCosemValue(0);
                    break;
                case GMETER_24H_DELIVERY_COMPENSATED_V2:
                    cosemValue = obj.getCosemValue(0);
                    break;
                case GMETER_VALUE_V3:
                    cosemValue = obj.getCosemValue(6);
                    break;
                case HMETER_VALUE_V2:
                    cosemValue = obj.getCosemValue(0);
                    break;
                case CMETER_VALUE_V2:
                    cosemValue = obj.getCosemValue(0);
                    break;
                case WMETER_VALUE_V2:
                    cosemValue = obj.getCosemValue(0);
                    break;
                case M3METER_VALUE:
                    cosemValue = obj.getCosemValue(1);
                    break;
                case GJMETER_VALUE_V4:
                    cosemValue = obj.getCosemValue(1);
                    break;
            }
            if (cosemValue != null) {
                newState = cosemValue.getOpenHABValue();
            }
        } else {
            logger.warn("Invalid CosemObject size ({}) for CosemObject {}", cosemValues.size(), obj);
        }
        if (newState != null) {
            updateState(obj.getType().name().toLowerCase(), newState);
        }
    }

    /**
     * Returns the DSMR meter
     *
     * @return {@link DSMRMeter}
     */
    public DSMRMeter getDSMRMeter() {
        return meter;
    }
}
