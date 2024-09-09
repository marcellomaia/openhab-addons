/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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

import static org.openhab.binding.apsystems.internal.apsystemsBindingConstants.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

//import io.netty.util.concurrent.ScheduledFuture;

/**
 * The {@link apsystemsHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Marcello Maia - Initial contribution
 */
@NonNullByDefault
public class apsystemsHandler extends BaseThingHandler {

    protected final HttpClient httpClient;
    private final Gson gson = new Gson();
    private int consecErrors = 0;
    private boolean firstUpdate = true;

    private boolean hasData = false;
    private QuantityType<?> lastPower;
    private QuantityType<?> lastEnergy;

    private final Logger logger = LoggerFactory.getLogger(apsystemsHandler.class);

    private @Nullable apsystemsConfiguration config;

    private @Nullable ScheduledFuture<?> pollingJob;

    public apsystemsHandler(Thing thing, HttpClient httpClient) {
        super(thing);
        this.httpClient = httpClient;
        this.lastEnergy = new QuantityType<>(0, Units.KILOWATT_HOUR);
        this.lastPower = new QuantityType<>(0, Units.WATT);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // logger.info("command for {}: {}", channelUID, command);
        if (command == RefreshType.REFRESH) {
            logger.info("responding Refresh request for {}", channelUID.getId());
            if (channelUID.getId().equals(CHANNEL_TODAY_ENERGY) && this.hasData) {
                updateState(CHANNEL_TODAY_ENERGY, this.lastEnergy);
            }
            if (channelUID.getId().equals(CHANNEL_CURRENT_POWER) && this.hasData) {
                updateState(CHANNEL_CURRENT_POWER, this.lastPower);
            }
        }
    }

    @Override
    public void initialize() {
        config = getConfigAs(apsystemsConfiguration.class);

        updateStatus(ThingStatus.UNKNOWN);

        if (config == null || config.hostname.length() == 0) {
            this.logger.error("Invalid hostname!");
            updateStatus(ThingStatus.OFFLINE);
        } else {
            this.logger.info(String.format("using host: %s with update frequency: %d...", config.hostname,
                    config.refreshInterval));

            updateStatus(ThingStatus.ONLINE);

            this.setupPooling();
        }
    }

    private void setupPooling() {
        this.cancelPooling();
        if (config != null) {
            this.logger.info(String.format("Enabling pooling of data with frequency: %d... from %s",
                    config.refreshInterval, config.hostname));
            this.pollingJob = scheduler.scheduleWithFixedDelay(this::getData, config.refreshInterval / 2,
                    config.refreshInterval, TimeUnit.SECONDS);
        }
    }

    private void getData() {
        if (!this.firstUpdate && (LocalTime.now().getHour() >= 20 || LocalTime.now().getHour() <= 5)) {
            this.logger.info("too late,  skipping retrieving data...");
            return;
        }

        if (this.config != null) {
            var url = String.format("http://%s/index.php/realtimedata/old_power_graph", this.config.hostname);
            this.logger.info("pooling ECU for updates @ {}...", this.config.hostname);
            var payload = String.format("{ \"date\": \"%s\" }", (LocalDate.now()).toString());

            Request request = httpClient.newRequest(url).timeout(this.config.refreshInterval / 3, TimeUnit.SECONDS)
                    .method(HttpMethod.POST).content(new StringContentProvider(payload))
                    .header(HttpHeader.CONTENT_TYPE, "application/json");

            try {
                ContentResponse response = request.send();
                if (response.getStatus() == 200) {
                    var resultString = response.getContentAsString();
                    JsonElement jsonDoc = gson.fromJson(resultString, JsonElement.class);
                    if (jsonDoc != null) {
                        JsonObject result = jsonDoc.getAsJsonObject();
                        var totHoje = result.get("today_energy");
                        if (totHoje != null) {
                            var rawVal = totHoje.getAsFloat();
                            var val = new QuantityType<>(rawVal, Units.KILOWATT_HOUR);
                            this.logger.debug(String.format("Today Energy: %s", val.toString()));
                            this.lastEnergy = val;
                            updateState(CHANNEL_TODAY_ENERGY, val);
                        }
                        if (result.get("power") != null) {
                            var powerArray = result.get("power").getAsJsonArray();
                            if (powerArray.size() > 0) {
                                var lastOne = powerArray.get(powerArray.size() - 1).getAsJsonObject();
                                if (lastOne.get("each_system_power") != null) {
                                    var rawVal = lastOne.get("each_system_power").getAsLong();
                                    var val = new QuantityType<>(rawVal, Units.WATT);
                                    /*
                                     * var n = Instant.now().toEpochMilli();
                                     * var epoch = lastOne.get("time").getAsLong();
                                     * var val = new QuantityType<>(0, Units.WATT);
                                     * if (Math.abs(epoch - n) < (5 * 60 * 1000)) {
                                     * var rawVal = lastOne.get("each_system_power").getAsLong();
                                     * val = new QuantityType<>(rawVal, Units.WATT);
                                     * }
                                     */
                                    this.lastPower = val;
                                    updateState(CHANNEL_CURRENT_POWER, val);
                                    this.logger.debug(String.format("Current Power: %s", val.toString()));
                                }
                            }
                        }
                    }
                }
                this.consecErrors = 0;
                this.firstUpdate = false;
                this.hasData = true;
                if (getThing().getStatus() != ThingStatus.ONLINE) {
                    updateStatus(ThingStatus.ONLINE);
                }
            } catch (InterruptedException | ExecutionException | TimeoutException exc) {
                this.consecErrors++;
                this.logger.error("Error trying to retrieve data from ECO");
                if (this.consecErrors >= 5) {
                    this.consecErrors = 5;
                    if (getThing().getStatus() != ThingStatus.OFFLINE) {
                        this.logger.warn("Too much errors, setting thing as OFFLINE");
                        updateStatus(ThingStatus.OFFLINE);
                    }
                }
            }
        }
    }

    @Override
    public void dispose() {
        this.cancelPooling();
    }

    private void cancelPooling() {
        this.firstUpdate = true;
        ScheduledFuture<?> future = this.pollingJob;
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        this.pollingJob = null;
    }
}
