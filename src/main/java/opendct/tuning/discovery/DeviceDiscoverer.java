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
import opendct.config.OSVersion;
import opendct.config.options.DeviceOptions;

public interface DeviceDiscoverer extends DeviceOptions {

    /**
     * This returns the friendly name of this discoverer implementation.
     *
     * @return The friendly name of the implementation.
     */
    public String getName();

    /**
     * This returns a brief description of what devices this discoverer detects.
     *
     * @return A description of the implementation.
     */
    public String getDescription();

    /**
     * Should this discovery method be started.
     *
     * @return <i>false</i> to ensure this discovery method is not started.
     */
    public boolean isEnabled();

    /**
     * Enable or disable the use of this discovery method.
     *
     * @param enabled Set <i>true</i> to enable this discovery method.
     */
    public void setEnabled(boolean enabled);

    /**
     * Returns an array of the OS versions supported by this discovery method.
     * <p/>
     * This is used to determine if loading this discovery method is not possible on the current OS.
     *
     * @return An array of supported OS versions.
     */
    public OSVersion[] getSupportedOS();

    /**
     * Starts detection using this discovery method.
     *
     * @param deviceLoader An implementation that will be used to add devices to
     * @throws DiscoveryException Thrown if the detection method fails to start.
     */
    public void startDetection(DeviceLoader deviceLoader) throws DiscoveryException;

    /**
     * Stops detection using this discovery method.
     * <p/>
     * If the discovery method stops on it's own before this is called and this is considered normal
     * behavior, do not throw an exception. Running threads are not required to have terminated
     * before this returns. This is called to cause detection to stop and cleanup. The entire
     * process should not be happening on this thread unless it is necessary.
     *
     * @throws DiscoveryException Thrown is detection method fails to stop.
     */
    public void stopDetection() throws DiscoveryException;

    /**
     * Waits for a previous stop detection request to complete.
     * <p/>
     * This is only called on suspend and shutdown. When this is called, any local cache of
     * discovered devices or methods to keep the capture devices up to date must be cleared before
     * this returns. Also all threads must be completely stopped before this returns. Typically all
     * discovery methods will receive stopDetection() sequentially so they can all start cleaning up
     * in parallel, then this method is called sequentially to ensure they have completely stopped.
     *
     */
    public void waitForStopDetection() throws InterruptedException;

    /**
     * Is this detection method currently running?
     *
     * @return <i>true</i> if detection is running.
     */
    public boolean isRunning();

    /**
     * Error message.
     * <p/>
     * This must return <i>null</i> if the discovery method does not currently have any issues.
     *
     * @return <i>null</i> or a message pertaining to the current error conditions.
     */
    public String getErrorMessage();

    /**
     * How many unique capture devices have been discovered?
     *
     * @return The number of capture devices.
     */
    public int discoveredDevices();

    /**
     * Returns currently detected capture devices details.
     * <p/>
     * Never return any devices that have not passed though the device loader first. Failure to do
     * so may result in devices appearing to be unloaded when in fact they are loading right now.
     *
     * @return The details of the unloaded capture devices.
     */
    public DiscoveredDevice[] getAllDeviceDetails();

    /**
     * Returns the requested capture device details.
     *
     * @param deviceId The capture device ID.
     * @return The details of the requested capture device.
     */
    public DiscoveredDevice getDeviceDetails(int deviceId);

    /**
     * Returns all capture device parents.
     *
     * @return The details of all available capture device parents.
     */
    public DiscoveredDeviceParent[] getAllDeviceParentDetails();

    /**
     * Returns the requested capture device parent.
     *
     * @param parentId The capture device parent ID.
     * @return The details of the requested capture device parent.
     */
    public DiscoveredDeviceParent getDeviceParentDetails(int parentId);

    /**
     * Creates the capture device implementation for the requested unloaded device.
     *
     * @param deviceId The name of the unloaded device.
     * @return A ready to use capture device.
     * @throws CaptureDeviceIgnoredException Thrown if this device isn't supposed to be loaded. This
     *                                       is a soon to be deprecated way to prevent capture
     *                                       devices from loading.
     * @throws CaptureDeviceLoadException Thrown if there is a problem preventing the device from
     *                                    being able to load.
     */
    public CaptureDevice loadCaptureDevice(int deviceId)
            throws CaptureDeviceIgnoredException, CaptureDeviceLoadException;
}
