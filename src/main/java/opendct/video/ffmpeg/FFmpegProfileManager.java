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

package opendct.video.ffmpeg;

import opendct.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.Map;

import static org.bytedeco.javacpp.avutil.av_dict_set;

public class FFmpegProfileManager {
    private final static Logger logger = LogManager.getLogger(FFmpegProfileManager.class);

    private final static HashMap<String, FFmpegProfile> profiles = new HashMap<>();

    static {
        File transcodeDir = new File(Config.getConfigDirectory() + Config.DIR_SEPARATOR + "transcode");
        File profiles[] = transcodeDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return (name.endsWith(".properties"));
            }
        });

        if (profiles != null) {
            for (File profile : profiles) {
                String profileName = profile.getName().substring(0, profile.getName().length() - ".properties".length());
                FFmpegProfileManager.profiles.put(profileName, new FFmpegProfile(profileName));
            }
        }
    }

    /**
     * Get a profile if it exists.
     * <p/>
     * This will also reload the profile from disk if the profile is set to reload on demand.
     *
     * @param profile The name of the profile.
     * @return The profile oor <i>null</i> if the profile doesn't exist.
     */
    public static FFmpegProfile getEncoderProfile(String profile) {
        FFmpegProfile returnValue = profiles.get(profile);

        if (returnValue != null) {
            // If it is set to not reload, this will return immediately.
            returnValue.reload();
        }

        return returnValue;
    }

    /**
     * Applies all encoder settings based on the configuration.
     *
     * @param settings A HashMap containing all of the settings to be used.
     * @param encoderContext The codec context to derive what settings should be used.
     * @param dict The dictionary to be used to apply codec specific options.
     */
    public static void confVideoEncoder(HashMap<String,String> settings, avcodec.AVCodecContext encoderContext, avutil.AVDictionary dict) {

        int iValue;
        float fValue;

        for (Map.Entry<String, String> setting : settings.entrySet()) {
            String key = setting.getKey();
            String value = setting.getValue();


            try {
                switch (key) {
                    case "bit_rate":
                        iValue = Integer.parseInt(value);
                        encoderContext.bit_rate(iValue);
                        break;
                    case "rc_max_rate":
                        iValue = Integer.parseInt(value);
                        encoderContext.rc_max_rate(iValue);
                        break;
                    case "rc_min_rate":
                        iValue = Integer.parseInt(value);
                        encoderContext.rc_min_rate(iValue);
                        break;
                    case "me_cmp":
                        iValue = Integer.parseInt(value);
                        encoderContext.me_cmp(iValue);
                        break;
                    case "me_range":
                        iValue = Integer.parseInt(value);
                        encoderContext.me_range(iValue);
                        break;
                    case "qmin":
                        iValue = Integer.parseInt(value);
                        encoderContext.qmin(iValue);
                        break;
                    case "qmax":
                        iValue = Integer.parseInt(value);
                        encoderContext.qmax(iValue);
                        break;
                    case "gop_size":
                        iValue = Integer.parseInt(value);
                        encoderContext.gop_size(iValue);
                        break;
                    case "trellis":
                        iValue = Integer.parseInt(value);
                        encoderContext.trellis(iValue);
                        break;
                    case "max_b_frames":
                        iValue = Integer.parseInt(value);
                        encoderContext.max_b_frames(iValue);
                        break;
                    case "deinterlace_filter":
                    case "progressive_filter":
                    case "encode_weight":
                        break;
                    default:
                        if (key.startsWith("dict") && key.length() > 4) {
                            String setKey = key.substring(5);
                            iValue = av_dict_set(dict, setKey, value, 0);
                            if (iValue < 0) {
                                logger.error("av_dict_set: Error {} while trying to add '{}' with the value '{}'", iValue, setKey, value);
                            }
                        } else {
                            logger.warn("'{}' was not set to '{}' because it is not an available option.",
                                    key, value);
                        }
                }
            } catch (NumberFormatException e) {
                logger.error("'{}' could not be parsed into a number for '{}'.", value, key);
            }
        }

        logger.info("Encoder settings: {}", settings);
    }
}
