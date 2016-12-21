/*
 * Copyright 2015-2016 The OpenDCT Authors. All Rights Reserved
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opendct.config;

public class StaticConfig {
    public static final int VERSION_CONFIG = 4;

    public static final int VERSION_MAJOR = 0;
    public static final int VERSION_MINOR = 5;
    public static final int VERSION_BUILD = 16;
    public static final String VERSION_PROGRAM =
            VERSION_MAJOR + "." + VERSION_MINOR + "." + VERSION_BUILD;

    public static final byte ENCODER_COMPATIBLE_MAJOR_VERSION = 4;
    public static final byte ENCODER_COMPATIBLE_MINOR_VERSION = 1;
    public static final byte ENCODER_COMPATIBLE_MICRO_VERSION = 0;
}
