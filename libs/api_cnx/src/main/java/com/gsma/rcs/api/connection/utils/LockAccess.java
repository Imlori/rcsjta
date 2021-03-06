/*******************************************************************************
 * Software Name : RCS IMS Stack
 * <p/>
 * Copyright (C) 2010-2016 Orange.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.gsma.rcs.api.connection.utils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An object to lock access to a resource
 *
 * @author Philippe LEMORDANT
 */
public class LockAccess {

    /**
     * The locker object
     */
    AtomicBoolean locker = new AtomicBoolean();

    /**
     * Acquires the lock only if it is not already locked at the time of invocation
     *
     * @return true is resource is unlocked
     */
    public boolean tryLock() {
        return locker.compareAndSet(false, true);
    }

    /**
     * Checks if access is locked
     *
     * @return True if access is locked
     */
    public boolean isLocked() {
        return locker.get();
    }
}
