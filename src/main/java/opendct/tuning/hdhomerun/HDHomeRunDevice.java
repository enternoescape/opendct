/*
 * Copyright 2015 The OpenDCT Authors. All Rights Reserved
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

package opendct.tuning.hdhomerun;

import opendct.tuning.hdhomerun.returns.HDHomeRunFeatures;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;

public class HDHomeRunDevice {
    public final HDHomeRunControl CONTROL;

    private InetAddress ipAddress;
    private int deviceType;
    private int deviceId;
    private int tunerCount;
    private boolean isLegacy;
    private String deviceAuth;
    private URL baseUrl;

    private HDHomeRunTuner tuners[];

    private String help;
    private String sysCopyright;
    private String sysDebug;
    private String sysFeatures;
    private String sysHwModel;
    private String sysModel;
    private String sysVersion;

    private String cardStatus;
    private Boolean isCable = null;

    public HDHomeRunDevice(InetAddress ipAddress) {
        this(ipAddress, -1, -1, 0, false, null, null);
    }

    public HDHomeRunDevice(InetAddress ipAddress, int deviceType, int deviceId, int tunerCount, boolean isLegacy, String deviceAuth, URL baseUrl) {
        CONTROL = new HDHomeRunControl();

        this.ipAddress = ipAddress;
        this.deviceType = deviceType;
        this.deviceId = deviceId;
        this.tunerCount = tunerCount;
        this.isLegacy = isLegacy;
        this.deviceAuth = deviceAuth;
        this.baseUrl = baseUrl;
    }

    @Override
    public int hashCode() {
        // The device ID must be unique per device.
        return deviceId;
    }

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    protected void setIpAddress(InetAddress ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getDeviceType() {
        return deviceType;
    }

    protected void setDeviceType(int deviceType) {
        this.deviceType = deviceType;
    }

    public int getDeviceId() {
        return deviceId;
    }

    protected void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public int getTunerCount() {
        return tunerCount;
    }

    protected void setTunerCount(int tunerCount) {
        this.tunerCount = tunerCount;

        tuners = new HDHomeRunTuner[tunerCount];

        for (int i = 0; i < tunerCount; i++) {
            tuners[i] = new HDHomeRunTuner(this, i);
        }
    }

    public boolean isLegacy() {
        return isLegacy;
    }

    protected void setLegacy(boolean legacy) {
        isLegacy = legacy;
    }

    public String getDeviceAuth() {
        return deviceAuth;
    }

    protected void setDeviceAuth(String deviceAuth) {
        this.deviceAuth = deviceAuth;
    }

    public URL getBaseUrl() {
        return baseUrl;
    }

    protected void setBaseUrl(URL baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Get a tuner by number for HDHomeRun device control.
     * <p/>
     * This always creates a new tuner instance, but retains the same socket for communication.
     *
     * @param tuner The tuner number.
     * @return A tuner device or <i>null</i> if the tuner number is greater than the number of
     * available tuners.
     */
    public HDHomeRunTuner getTuner(int tuner) {
        if (tuner > tunerCount) {
            return null;
        }

        return new HDHomeRunTuner(this, tuner, tuners[tuner].CONTROL);
    }

    /**
     * Get help on what configuration options are available on this device.
     * <p/>
     * This could be used to verify that a device supports a setting when performing initial
     * configuration.
     *
     * @return An array of every supported configuration option available on this device.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String[] getHelp() throws IOException, GetSetException {
        help = CONTROL.getVariable(ipAddress, "help");

        if (help.contains("\n")) {
            return help.split("\n");
        } else {
            return new String[]{help};
        }
    }

    /**
     * Get the copyright notice for this device.
     *
     * @return The copyright notice.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String getSysCopyright() throws IOException, GetSetException {
        if (sysCopyright == null) {
            sysCopyright = CONTROL.getVariable(ipAddress, "/sys/copyright");
        }

        return sysCopyright;
    }

    /**
     * Get debug information for Silicondust.
     *
     * @return Returns debug information for Silicondust.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String[] getSysDebug() throws IOException, GetSetException {
        sysDebug = CONTROL.getVariable(ipAddress, "/sys/debug");

        if (sysDebug.contains("\n")) {
            return sysDebug.split("\n");
        } else {
            return new String[]{sysDebug};
        }
    }

    /**
     * Get system features information.
     *
     * @return Returns system features information.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public HDHomeRunFeatures getSysFeatures() throws IOException, GetSetException {
        sysFeatures = CONTROL.getVariable(ipAddress, "/sys/features");

        return new HDHomeRunFeatures(sysFeatures);
    }

    /**
     * Get the system hardware model name.
     * <p/>
     * This is the preferred string for automatically naming HDHomeRun devices.
     *
     * @return Returns system hardware model name.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String getSysHwModel() throws IOException, GetSetException {
        if (sysHwModel == null) {
            sysHwModel = CONTROL.getVariable(ipAddress, "/sys/hwmodel");
        }

        return sysHwModel;
    }

    /**
     * Get the system model name.
     *
     * @return Returns system model name.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String getSysModel() throws IOException, GetSetException {
        if (sysModel == null) {
            sysModel = CONTROL.getVariable(ipAddress, "/sys/model");
        }

        return sysModel;
    }

    /**
     * Get the system firmware version.
     *
     * @return Returns system firmware version.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String getSysVersion() throws IOException, GetSetException {
        sysVersion = CONTROL.getVariable(ipAddress, "/sys/version");

        return sysVersion;
    }

    /**
     * Get the status of the CableCARD if this device supports one.
     * <p/>
     * If this device does not support this option, an exception will be thrown.
     *
     * @return Returns the status of the CableCARD.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String getCardStatus() throws IOException, GetSetException {
        cardStatus = CONTROL.getVariable(ipAddress, "/card/status");

        return cardStatus;
    }

    /**
     * Get if this is a CableCARD tuning device.
     *
     * @return <i>true</i> if this is a CableCARD tuning device.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     */
    public boolean isCableCardTuner() throws IOException {
        if (isCable == null) {
            try {
                getCardStatus();
                isCable = true;
            } catch (GetSetException e) {
                isCable = false;
            }
        }

        return isCable;
    }

    /**
     * Get a unique name for a tuner on this device.
     * <p/>
     * This name is also formatted for display purposes.
     *
     * @return Returns unique name for tuner.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String getUniqueDeviceName() throws IOException, GetSetException {
        if (sysHwModel == null) {
            getSysHwModel();
        }

        return "HDHomeRun " + sysHwModel + " " + deviceId;
    }

    /**
     * Get a unique name for a tuner on this device.
     * <p/>
     * This name is also formatted for display purposes.
     *
     * @param tuner The tuner number to append to the rest of the unique name.
     * @return Returns unique name for tuner.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String getUniqueTunerName(int tuner) throws IOException, GetSetException {
        if (sysHwModel == null) {
            getSysHwModel();
        }

        return "HDHomeRun " + sysHwModel + " Tuner " + deviceId + "-" + String.valueOf(tuner);
    }
}
