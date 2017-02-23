package org.openhab.binding.dsmr.meter;

/**
 * This class describes the configuration for a meter.
 *
 * This class is supporting the Configuration.as functionality from {@link Configuration}
 *
 * @author M. Volaart
 * @since 2.1.0
 */
public class DSMRMeterConfiguration {
    // channel
    public int channel;

    @Override
    public String toString() {
        return "DSMRMeterConfiguration(channel:" + channel + ")";
    }

    /**
     * Returns if this DSMRMeterConfiguration is equal to the other DSMRMeterConfiguration.
     * Evaluation is done based on all the parameters
     *
     * @param other the other DSMRMeterConfiguration to check
     * @return true if both are equal, false otherwise or if other == null
     */
    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof DSMRMeterConfiguration)) {
            return false;
        }
        DSMRMeterConfiguration o = (DSMRMeterConfiguration) other;

        return channel == o.channel;
    }
}
