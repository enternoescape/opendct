/*
 * Copyright 2016 The OpenDCT Authors. All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opendct.tuning.discovery;

import opendct.capture.CaptureDevice;
import opendct.capture.CaptureDeviceIgnoredException;
import opendct.config.options.DeviceOptions;

public interface DiscoveredDevice extends DeviceOptions {

    /**
     * The unique name of this capture device.
     * <p/>
     * This should always return exactly the same name every time this device is detected. This is
     * used to verify that we are not potentially loading a duplicate device.
     *
     * @return The unchangeable unique name of this capture device.
     */
    public String getName();

    /**
     * The friendly/modifiable name of this capture device.
     * <p/>
     * This can be the same as the unique name, but this value should be user assignable.
     *
     * @return The modifiable name of this capture device.
     */
    public String getFriendlyName();

    /**
     * The unique id of this capture device.
     * <p/>
     * This ID must be exactly the same every time this device is detected. This is used to verify
     * that we are not potentially loading a duplicate device.
     *
     * @return The unique ID for this capture device.
     */
    public int getId();

    /**
     * The unique id of this capture devices parent.
     * <p/>
     * This ID must be exactly the same every time this device is detected.
     *
     * @return The unique ID of the parent for this capture device.
     */
    public int getParentId();

    /**
     * A brief description of this capture device.
     * <p/>
     * This is displayed on the web interface. Try to include in the description at a minimum a hint
     * of how it was discovered.
     *
     * @return A description of the capture device.
     */
    public String getDescription();

    /**
     * /**
     * Creates the capture device implementation for this device.
     *
     * @return A ready to use capture device.
     * @throws CaptureDeviceIgnoredException Thrown if this device isn't supposed to be loaded. This
     *                                       is a soon to be deprecated way to prevent capture
     *                                       devices from loading.
     * @throws CaptureDeviceLoadException Thrown if there is a problem preventing the device from
     *                                    being able to load.
     */
    public CaptureDevice loadCaptureDevice()
            throws CaptureDeviceIgnoredException, CaptureDeviceLoadException;
}
