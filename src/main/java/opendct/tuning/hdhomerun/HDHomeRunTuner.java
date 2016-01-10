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

import opendct.tuning.hdhomerun.returns.HDHomeRunStatus;
import opendct.tuning.hdhomerun.returns.HDHomeRunStreamInfo;
import opendct.tuning.hdhomerun.returns.HDHomeRunVStatus;
import opendct.util.Util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;

public class HDHomeRunTuner {
    public final HDHomeRunDevice DEVICE;
    public final int TUNER_NUMBER;
    public final HDHomeRunControl CONTROL;

    private String status;
    private String streamInfo;
    private String channel;
    private String program;
    private String lockkey; //This is the device lock.
    private String vChannel;
    private String vstatus;

    private String channelmap;
    private String debug;
    private String filter;
    private String target;

    private int currentLockkey;

    public HDHomeRunTuner(HDHomeRunDevice device, int tuner) {
        this(device, tuner, new HDHomeRunControl());
    }

    public HDHomeRunTuner(HDHomeRunDevice device, int tuner, HDHomeRunControl control) {
        DEVICE = device;
        TUNER_NUMBER = tuner;
        CONTROL = control;

        currentLockkey = -1;

        // This will attempt to set the lock key in the correct state.
        try {
            isLockedByThisComputer();
        } catch (Exception e) {
            // It doesn't really matter if this fails or not at this moment.
        }
    }

    /**
     * Get a list of all values returned from calling the get methods in this class.
     * <p/>
     * This can be used for debugging, but it could also be used to get data without communicating
     * with the device.
     *
     * @return A String array always with a length of 7. If a request was never successful for a
     * value, it will be null. The list is in the following order: status, streaminfo,
     * channel, program, lockkey, vchannel, vstatus, channelmap, debug, filter, target.
     */
    public String[] getLastReturnValues() {
        String returnValue[] = new String[11];

        returnValue[0] = status;
        returnValue[1] = streamInfo;
        returnValue[2] = channel;
        returnValue[3] = program;
        returnValue[4] = lockkey;
        returnValue[5] = vChannel;
        returnValue[6] = vstatus;
        returnValue[7] = channelmap;
        returnValue[8] = debug;
        returnValue[9] = filter;
        returnValue[10] = target;

        return returnValue;
    }

    private String getTunerGetString(String key) {
        return "/tuner" + TUNER_NUMBER + "/" + key;
    }

    /**
     * Custom get for a tuner on the device.
     *
     * @param key The key to get.
     * @return A value from the device. If the reply was invalid, this will be <i>null</i>.
     * @throws IOException     Thrown if communication with the device was incomplete or is not possible
     *                         at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String get(String key) throws IOException, GetSetException {
        return CONTROL.getVariable(DEVICE.getIpAddress(), getTunerGetString(key));
    }

    /**
     * Custom set for a tuner on the device.
     *
     * @param key   The key to set.
     * @param value The value to set for the key.
     * @return A value from the device. If the reply was invalid, this will be <i>null</i>.
     * @throws IOException     Thrown if communication with the device was incomplete or is not possible
     *                         at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String set(String key, String value) throws IOException, GetSetException {
        if (currentLockkey > -1) {
            return CONTROL.setVariable(DEVICE.getIpAddress(), getTunerGetString(key), value, currentLockkey);
        }

        return CONTROL.setVariable(DEVICE.getIpAddress(), getTunerGetString(key), value);
    }

    /**
     * Custom set for a tuner on the device.
     *
     * @param key     The key to set.
     * @param value   The value to set for the key.
     * @param lockkey The lockkey needed to set the key.
     * @return A value from the device. If the reply was invalid, this will be <i>null</i>.
     * @throws IOException     Thrown if communication with the device was incomplete or is not possible
     *                         at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String set(String key, String value, int lockkey) throws IOException, GetSetException {
        return CONTROL.setVariable(DEVICE.getIpAddress(), getTunerGetString(key), value, lockkey);
    }

    /**
     * Get the currently tuned program.
     *
     * @return The program or 0 if no program is currently tuned.
     * @throws IOException     Thrown if communication with the device was incomplete or is not possible
     *                         at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public int getProgram() throws IOException, GetSetException {
        program = get("program");

        try {
            return Integer.valueOf(program);
        } catch (NumberFormatException e) {

        }

        return 0;
    }

    /**
     * Set the currently tuned program.
     *
     * @param program The program to select.
     * @throws IOException     Thrown if communication with the device was incomplete or is not possible
     *                         at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void setProgram(String program) throws IOException, GetSetException {
        set("program", program);
    }

    /**
     * Get stream information.
     * <p/>
     * This includes a list of all currently available programs and the current tsid.
     *
     * @return Stream info object.
     * @throws IOException     Thrown if communication with the device was incomplete or is not possible
     *                         at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public HDHomeRunStreamInfo getStreamInfo() throws IOException, GetSetException {
        streamInfo = get("streaminfo");

        return (new HDHomeRunStreamInfo(streamInfo));
    }

    /**
     * Get the currently tuned modulation and frequency/channel.
     * <p/>
     * This is not the same as vchannel. It should contain modulation:frequency/channel.
     *
     * @return The current channel.
     * @throws IOException     Thrown if communication with the device was incomplete or is not possible
     *                         at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String getChannel() throws IOException, GetSetException {
        channel = get("channel");

        return channel;
    }

    /**
     * Set the modulation and frequency/channel.
     *
     * @param modulation This is the modulation to be used.
     * @param frequencyChannel This is the frequency or channel to tune into.
     * @param channel <i>true</i> if this is not a frequency.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void setChannel(String modulation, String frequencyChannel, boolean channel) throws IOException, GetSetException {
        set("channel", modulation.toLowerCase() + ":" + frequencyChannel);
    }

    /**
     * Clears the current modulation and frequency/channel.
     *
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void clearChannel() throws IOException, GetSetException {
        set("channel", "none");
    }

    /**
     * Get the current lock key.
     *
     * @return The current lock key.
     * @throws IOException     Thrown if communication with the device was incomplete or is not possible
     *                         at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String getLockkey() throws IOException, GetSetException {
        lockkey = get("lockkey");

        if (lockkey == null) {
            lockkey = "none";
        }

        return lockkey;
    }

    /**
     * Is the device locked by any interface available on this computer?
     *
     * @return <i>1</i> if this computer has the lock. <i>0</i> if this computer does not
     *         have the lock. <i>-1</i> if no lock is set.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public int isLockedByThisComputer() throws IOException, GetSetException {
        if (!getLockkey().contains("none")) {
            try {
                InetAddress lockAddress = InetAddress.getByName(lockkey);
                InetAddress localAddress = Util.getLocalIPForRemoteIP(lockAddress);

                if (localAddress != null && localAddress.equals(lockAddress)) {
                    currentLockkey = Math.abs(lockAddress.hashCode());
                    return 1;
                } else {
                    return 0;
                }
            } catch (Exception e) {
                throw new GetSetException(e);
            }
        }

        return -1;
    }

    /**
     * Is this tuner currently locked?
     *
     * @return <i>true</i> if the tuner is locked.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public boolean isLocked() throws IOException, GetSetException {
        return !getLockkey().contains("none");
    }

    /**
     * Clears the current lockkey.
     *
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void clearLockkey() throws IOException, GetSetException {
        if (getLockkey().contains("none")) {
            return;
        }

        set("lockkey", "none", currentLockkey);

        if (getLockkey().contains("none")) {
            currentLockkey = -1;
        } else {
            throw new GetSetException("The tuner is not locked by this computer. It is locked by" +
                    " a computer with the IP address '" + lockkey + "'.");
        }
    }

    /**
     * Clears the current lockkey regardless of what device has a lock on it.
     *
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void forceClearLockkey() throws IOException, GetSetException {
        set("lockkey", "force", currentLockkey);

        if (getLockkey().contains("none")) {
            currentLockkey = -1;
        } else {
            throw new GetSetException("Unable to force the lock to be removed. It is locked by" +
                    " a computer with the IP address '" + lockkey + "'.");
        }
    }

    /**
     * Clears the current lockkey if it was set by OpenDCT on any other computer/interface.
     *
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void forceKnownClearLockkey() throws IOException, GetSetException {
        if (getLockkey().contains("none")) {
            return;
        }

        InetAddress lockAddress = InetAddress.getByName(lockkey);

        set("lockkey", "none", Math.abs(lockAddress.hashCode()));

        currentLockkey = -1;
    }

    /**
     * Set the current lockkey.
     * <p/>
     * <b>GetSetException</b> will be thrown if the device is already locked and it's not locked by
     * an interface on this computer. If another computer uses this same lockkey number, it can take
     * the lock away from this computer.
     *
     * @param lockkeyAddress The value to set the lockkey. This does not actually set the IP address
     *                       of the lock. That is done automatically by the device based on the
     *                       requesting IP address.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void setLockkey(InetAddress lockkeyAddress) throws IOException, GetSetException {
        if (lockkeyAddress == null) {
            return;
        }

        set("lockkey", String.valueOf(Math.abs(lockkeyAddress.hashCode())));

        currentLockkey = Math.abs(lockkeyAddress.hashCode());
    }

    /**
     * Set the current lockkey.
     * <p/>
     * <b>GetSetException</b> will be thrown if the device is already locked and it's not locked by
     * an interface on this computer. If another computer uses this same lockkey number, it can take
     * the lock away from this computer. An OpenDCT "compliant" lockkey is based on the hashcode of
     * the locking IP address. This compliance allows OpenDCT to forcible override the lock if
     * needed from a computer other than the locking computer.
     *
     * @param lockkey The value to set the lockkey.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void setLockkey(int lockkey) throws IOException, GetSetException {
        set("lockkey", String.valueOf(Math.abs(lockkey)));

        currentLockkey = Math.abs(lockkey);
    }

    /**
     * Get the current status of this tuner on the device.
     *
     * @return The current status values.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public HDHomeRunStatus getStatus() throws IOException, GetSetException {
        status = CONTROL.getVariable(DEVICE.getIpAddress(), getTunerGetString("status"));

        return new HDHomeRunStatus(status);
    }

    /**
     * Get the current virtual channel status of this tuner on the device.
     *
     * @return The current vstatus values.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public HDHomeRunVStatus getVirtualChannelStatus() throws IOException, GetSetException {
        vstatus = CONTROL.getVariable(DEVICE.getIpAddress(), getTunerGetString("vstatus"));

        return new HDHomeRunVStatus(vstatus);
    }

    /**
     * Get the currently tuned virtual channel on this tuner on the device.
     *
     * @return The current virtual channel value.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String getVirtualChannel() throws IOException, GetSetException {
        vChannel = get("vchannel");

        return vChannel;
    }

    /**
     * Set the currently tuned virtual channel on this tuner on the device.
     * <p/>
     * This only works when a CableCARD is present.
     *
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void setVirtualChannel(String vChannel) throws IOException, GetSetException {
        set("vchannel", vChannel);
    }

    /**
     * Clears the current virtual Channel.
     *
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void clearVirtualChannel() throws IOException, GetSetException {
        setVirtualChannel("none");
    }

    /**
     * Get the current streaming target for this tuner on the device.
     *
     * @return The current streaming target.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String getTarget() throws IOException, GetSetException {
        target = get("target");

        return target;
    }

    /**
     * Clears the current streaming target for this tuner on the device.
     *
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void clearTarget() throws IOException, GetSetException {
        set("target", "none");
    }

    /**
     * Set the current streaming target for this tuner on the device.
     * <p/>
     * This is the preferred method since it enforces creating a valid URI before attempting to set
     * the value on the device.
     *
     * @param target The new target
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void setTarget(URI target) throws IOException, GetSetException {
        set("target", target.toString());
    }

    /**
     * Set the current streaming target for this tuner on the device.
     * <p/>
     * Valid values are:<p/>
     * none<p/>
     * udp://x.x.x.x:xxxx<p/>
     * rtp://x.x.x.x:xxxx
     *
     * @param target The new target
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void setTarget(String target) throws IOException, GetSetException {
        set("target", target);
    }

    /**
     * Get the current channel map in use for this tuner on the device.
     *
     * @return The current channel map.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String getChannelmap() throws IOException, GetSetException {
        channelmap = get("channelmap");

        return channelmap;
    }

    /**
     * Set the current channel map in use for this tuner on the device.
     * <p/>
     * Valid values are derived from /sys/features. Ex. us-cable
     *
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void setChannelmap(String channelmap) throws IOException, GetSetException {
        set("channelmap", channelmap);
    }

    /**
     * Get debug information for Silicondust for this tuner on the device.
     *
     * @return Returns debug information for Silicondust.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public String getDebug() throws IOException, GetSetException {
        debug = get("debug");

        return debug;
    }

    /**
     * Get currently in use PID filters for this tuner on the device.
     *
     * @return Returns current PID filters.
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error or unparsable data instead of a
     *                         value.
     */
    public int[] getFilter() throws IOException, GetSetException {
        filter = get("filter");

        String spaceSplit[] = filter.split(" ");

        ArrayList<Integer> pids = new ArrayList<Integer>();

        try {
            for (String space : spaceSplit) {
                int dash = space.indexOf("-");

                if (dash == -1) {
                    if (!space.startsWith("0x")) {
                        throw new GetSetException("The value '" + space + "' is not in the expected format.");
                    }

                    pids.add(Integer.parseInt(space.substring(2).trim(), 16));
                } else {
                    String startString = space.substring(0, dash);
                    String endString = space.substring(dash + 1);

                    if (!startString.startsWith("0x")) {
                        throw new GetSetException("The value '" + startString + "' is not in the expected format.");
                    }

                    if (!endString.startsWith("0x")) {
                        throw new GetSetException("The value '" + endString + "' is not in the expected format.");
                    }

                    int start = Integer.parseInt(startString.substring(2).trim(), 16);
                    int end = Integer.parseInt(endString.substring(2).trim(), 16) + 1;

                    while (start < end) {
                        pids.add(start++);
                    }
                }
            }
        } catch (NumberFormatException e) {
            return new int[0];
        }

        int returnValue[] = new int[pids.size()];

        for (int i = 0; i < returnValue.length; i++) {
            returnValue[i] = pids.get(i);
        }

        return returnValue;
    }

    /**
     * Set currently in use PID filters for this tuner on the device.
     * <p/>
     * Format: 0x<nnnn>-0x<nnnn> [...]
     *
     * @throws IOException Thrown if communication with the device was incomplete or is not possible
     *                     at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public void setFilter(String filter) throws IOException, GetSetException {
        set("filter", filter);
    }
}
