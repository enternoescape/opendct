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

package opendct.tuning.hdhomerun.returns;

import opendct.channel.CopyProtection;

public class HDHomeRunVStatus {
    public final String VCHANNEL;
    public final String NAME;
    public final String AUTH;
    public final String CCI;
    public final CopyProtection COPY_PROTECTION;
    public final String CGMS;
    public final boolean NOT_SUBSCRIBED;
    public final boolean NOT_AVAILABLE;
    public final boolean COPY_PROTECTED;

    public HDHomeRunVStatus(String vChannel, String name, String auth, String cci, CopyProtection copyProtection, String cgms, boolean notSubscribed, boolean notAvailable, boolean copyProtected) {
        VCHANNEL = vChannel;
        NAME = name;
        AUTH = auth;
        CCI = cci;
        COPY_PROTECTION = copyProtection;
        CGMS = cgms;
        NOT_SUBSCRIBED = notSubscribed;
        NOT_AVAILABLE = notAvailable;
        COPY_PROTECTED = copyProtected;
    }

    public HDHomeRunVStatus(String vstatus) {
        String vChannel = "";
        String name = "";
        String auth = "";
        String cci = "";
        CopyProtection copyProtection;
        String cgms = "";
        boolean notSubscribed;
        boolean notAvailable;
        boolean copyProtected;

        String split[] = vstatus.split(" ");

        for (String section : split) {
            if (!section.contains("=")) {
                continue;
            }

            String keyValuePair[] = section.split("=");
            if (keyValuePair.length != 2) {
                continue;
            }

            String key = keyValuePair[0];
            String value = keyValuePair[1];

            try {
                if (key.equals("vch")) {
                    vChannel = value;
                } else if (key.equals("name")) {
                    name = value;
                } else if (key.equals("auth")) {
                    auth = value;
                } else if (key.equals("cci")) {
                    cci = value;
                } else if (key.equals("cgms")) {
                    cgms = value;
                }
            } catch (NumberFormatException e) {

            }
        }

        notSubscribed = false;
        if (auth.contains("not-subscribed")) {
            notSubscribed = true;
        }

        notAvailable = false;
        if (auth.contains("error")) {
            notAvailable = true;
        } else if (auth.contains("dialog")) {
            notAvailable = true;
        }

        copyProtected = false;
        if (cci.contains("protected")) {
            copyProtected = true;
        } else if (cgms.contains("protected")) {
            copyProtected = true;
        }

        // The HDHomeRun doesn't seem to distinguish between NEVER and ONCE.
        if (cci.contains("none") && !notSubscribed) {
            copyProtection = CopyProtection.NONE;
        } else if (cci.contains("unrestricted")) {
            copyProtection = CopyProtection.COPY_FREELY;
        } else if (copyProtected) {
            copyProtection = CopyProtection.COPY_ONCE;
        } else {
            copyProtection = CopyProtection.COPY_NEVER;
        }

        VCHANNEL = vChannel;
        NAME = name;
        AUTH = auth;
        CCI = cci;
        COPY_PROTECTION = copyProtection;
        CGMS = cgms;
        NOT_SUBSCRIBED = notSubscribed;
        NOT_AVAILABLE = notAvailable;
        COPY_PROTECTED = copyProtected;
    }
}
