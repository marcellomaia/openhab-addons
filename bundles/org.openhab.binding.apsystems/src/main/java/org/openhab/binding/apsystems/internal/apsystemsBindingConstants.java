/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.apsystems.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link apsystemsBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Marcello Maia - Initial contribution
 */
@NonNullByDefault
public class apsystemsBindingConstants {

    private static final String BINDING_ID = "apsystems";
    public static final String DEVICE_GENERIC = "generic";

    public static final ThingTypeUID THING_TYPE_APSYS_ECU = new ThingTypeUID(BINDING_ID, "ecu");

    public static final String CHANNEL_CURRENT_POWER = "power";
    public static final String CHANNEL_TODAY_ENERGY = "energy";
}
