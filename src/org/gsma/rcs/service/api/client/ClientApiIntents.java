/*
 * Copyright 2013, France Telecom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gsma.rcs.service.api.client;

/**
 * Class ClientApiIntents.
 */
public class ClientApiIntents {
    /**
     * Constant SERVICE_STATUS_STARTING.
     */
    public static final int SERVICE_STATUS_STARTING = 0;

    /**
     * Constant SERVICE_STATUS_STARTED.
     */
    public static final int SERVICE_STATUS_STARTED = 1;

    /**
     * Constant SERVICE_STATUS_STOPPING.
     */
    public static final int SERVICE_STATUS_STOPPING = 2;

    /**
     * Constant SERVICE_STATUS_STOPPED.
     */
    public static final int SERVICE_STATUS_STOPPED = 3;

    /**
     * Constant SERVICE_STATUS_FAILED.
     */
    public static final int SERVICE_STATUS_FAILED = 4;

    /**
     * Constant RCS_SETTINGS.
     */
    public static final String RCS_SETTINGS = "org.gsma.rcs.SETTINGS";

    /**
     * Constant SERVICE_STATUS.
     */
    public static final String SERVICE_STATUS = "org.gsma.rcs.SERVICE_STATUS";

    /**
     * Creates a new instance of ClientApiIntents.
     */
    public ClientApiIntents() {

    }

} // end ClientApiIntents