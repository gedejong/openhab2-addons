/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.pioneeravr.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link PioneerAvrBinding} class defines common constants, which are used across the whole binding.
 *
 * @author Antoine Besnard - Initial contribution
 */
@NonNullByDefault
public class PioneerAvrBindingConstants {

    public static final String BINDING_ID = "pioneeravr";

    public static final Set<String> SUPPORTED_DEVICE_MODELS = Collections
            .unmodifiableSet(Arrays
                    .asList("SC-57", "SC-LX85", "SC-55", "SC-1526", "SC-LX75", "VSX-53", "VSX-1326", "VSX-LX55",
                            "VSX-2021", "VSA-LX55", "VSX-52", "VSX-1126", "VSX-1121", "VSX-51", "VSX-1021", "VSX-1026",
                            "VSA-1021", "VSX-50", "VSX-926", "VSX-921", "VSA-921")
                    .stream().collect(Collectors.toSet()));

    public static final Set<String> SUPPORTED_DEVICE_MODELS2014 = Collections
            .unmodifiableSet(Arrays.asList("SC-LX87", "SC-LX77", "SC-LX57", "SC-2023", "SC-1223", "VSX-1123", "VSX-923")
                    .stream().collect(Collectors.toSet()));

    public static final Set<String> SUPPORTED_DEVICE_MODELS2015 = Collections.unmodifiableSet(Arrays
            .asList("SC-89", "SC-LX88", "SC-87", "SC-LX78", "SC-85", "SC-LX58", "SC-82", "SC-2024", "SC-81", "VSX-80")
            .stream().collect(Collectors.toSet()));

    public static final Set<String> SUPPORTED_DEVICE_MODELS2016 = Collections.unmodifiableSet(Arrays
            .asList("SC-99", "SC-LX89", "SC-97", "SC-LX79", "SC-95", "SC-LX59", "SC-92", "SC-91", "VSX-90", "VSX-45")
            .stream().collect(Collectors.toSet()));

    // List of all Thing Type UIDs
    public static final ThingTypeUID IP_AVR_THING_TYPE = new ThingTypeUID(BINDING_ID, "ipAvr");
    public static final ThingTypeUID IP_AVR_THING_TYPE2014 = new ThingTypeUID(BINDING_ID, "ipAvr2014");
    public static final ThingTypeUID IP_AVR_THING_TYPE2015 = new ThingTypeUID(BINDING_ID, "ipAvr2015");
    public static final ThingTypeUID IP_AVR_THING_TYPE2016 = new ThingTypeUID(BINDING_ID, "ipAvr2016");
    public static final ThingTypeUID IP_AVR_UNSUPPORTED_THING_TYPE = new ThingTypeUID(BINDING_ID, "ipAvrUnsupported");
    public static final ThingTypeUID SERIAL_AVR_THING_TYPE = new ThingTypeUID(BINDING_ID, "serialAvr");

    // List of thing parameters names
    public static final String PROTOCOL_PARAMETER = "protocol";
    public static final String HOST_PARAMETER = "address";
    public static final String TCP_PORT_PARAMETER = "tcpPort";
    public static final String SERIAL_PORT_PARAMETER = "serialPort";

    public static final String IP_PROTOCOL_NAME = "IP";
    public static final String SERIAL_PROTOCOL_NAME = "serial";

    // List of all Channel names
    public static final String POWER_CHANNEL = "power";
    public static final String VOLUME_DIMMER_CHANNEL = "volumeDimmer";
    public static final String VOLUME_DB_CHANNEL = "volumeDb";
    public static final String MUTE_CHANNEL = "mute";
    public static final String SET_INPUT_SOURCE_CHANNEL = "setInputSource";
    public static final String DISPLAY_INFORMATION_CHANNEL = "displayInformation#displayInformation";

    public static final String GROUP_CHANNEL_PATTERN = "zone%s#%s";
    public static final Pattern GROUP_CHANNEL_ZONE_PATTERN = Pattern.compile("zone([0-4])#.*");

    // Used for Discovery service
    public static final String MANUFACTURER = "PIONEER";
    public static final String UPNP_DEVICE_TYPE = "MediaRenderer";

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Arrays.asList(IP_AVR_THING_TYPE,
            IP_AVR_THING_TYPE2016, IP_AVR_THING_TYPE2015, IP_AVR_THING_TYPE2014, IP_AVR_UNSUPPORTED_THING_TYPE).stream()
            .collect(Collectors.toSet());

}
