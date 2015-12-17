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

package opendct.sagetv;

/*
 This could be used for a multi-threaded tuner polling. Since it's possible that sometimes the first
 queried capture device is available, this would be a waste of resources as a first resort. Maybe
 after 100ms have passed and we still don't have a capture device, the remaining capture devices get
 queried on their own threads. Or we can use a thread pool to limit the number of devices being
 queried at one time.
 */
public class QueuedPoolDevice {
    public final String POOL_CAPTURE_DEVICE_NAME;
    public final boolean IS_LOCKED;
    public final boolean IS_EXTERNAL_LOCKED;
    public final int MERIT;

    public QueuedPoolDevice(String poolCaptureDeviceName, boolean isLocked, boolean isExternalLocked, int Merit) {
        POOL_CAPTURE_DEVICE_NAME = poolCaptureDeviceName;
        IS_LOCKED = isLocked;
        IS_EXTERNAL_LOCKED = isExternalLocked;
        MERIT = Merit;
    }

    public int compare(QueuedPoolDevice x, QueuedPoolDevice y) {

        // When they are locked for recording, they are always the same priority because we can't
        // use them anyway.
        if (x.IS_LOCKED && y.IS_LOCKED) {
            return 0;
        } else if (x.IS_LOCKED) {
            return -1;
        } else if (y.IS_LOCKED) {
            return 1;
        }

        if (x.IS_EXTERNAL_LOCKED && !y.IS_EXTERNAL_LOCKED) {
            return -1;
        } else if (!x.IS_EXTERNAL_LOCKED && y.IS_EXTERNAL_LOCKED) {
            return 1;
        }

        // All other things being equal.
        return x.MERIT - y.MERIT;
    }
}
