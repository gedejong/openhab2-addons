package org.openhab.binding.dsmr.meter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The DSMRMeterDescriptor describes a meter.
 *
 * A DSMR Meter consists of the following properties:
 * - MeterType
 * - M-Bus channel
 * - Identifier
 *
 * @author M. Volaart
 * @since 2.0.0
 */
public class DSMRMeterDescriptor {
    // logger
    private static final Logger logger = LoggerFactory.getLogger(DSMRMeterDescriptor.class);

    // Meter type
    private final DSMRMeterType meterType;

    // M-Bus channel
    private final Integer channel;

    /**
     *
     * @param meterType
     * @param channel
     * @throws IllegalArgumentException if one of the parameters is null
     */
    public DSMRMeterDescriptor(DSMRMeterType meterType, Integer channel) {
        if (meterType == null || channel == null) {
            logger.error("MeterType: {}, channel:{}, idString:{}", meterType, channel);

            throw new IllegalArgumentException("Parameters of DSMRMeterDescription can not be null");
        }

        this.meterType = meterType;
        this.channel = channel;
    }

    /**
     * @return the meterType
     */
    public DSMRMeterType getMeterType() {
        return meterType;
    }

    /**
     * @return the channel
     */
    public Integer getChannel() {
        return channel;
    }

    /**
     * Returns true if both DSMRMeterDescriptor are equal. I.e.:
     * - meterType is the same
     * - channel is the same
     * - identification is the same
     *
     * @param other DSMRMeterDescriptor to check
     * @return true if both objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof DSMRMeterDescriptor)) {
            return false;
        }
        DSMRMeterDescriptor o = (DSMRMeterDescriptor) other;

        return meterType == o.meterType && channel.equals(o.channel);
    }

    @Override
    public String toString() {
        return "Meter type: " + meterType + ", channel: " + channel;
    }
}
