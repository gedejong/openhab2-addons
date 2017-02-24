/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.dsmr.internal.device;

import org.openhab.binding.dsmr.internal.device.DSMRDeviceConstants.DSMRPortEvent;

/**
 * Interface for handling DSMRPortEvent events
 *
 * @author M. Volaart
 * @since 2.1.0
 */
public interface DSMRPortEventListener {
    /**
     * Callback for DSMRPortEvent events
     *
     * @param portEvent {@link DSMRPortEvent} that has occurred
     */
    public void handleDSMRPortEvent(DSMRPortEvent portEvent);
}
