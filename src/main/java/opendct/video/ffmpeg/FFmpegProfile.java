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

import opendct.config.ConfigBag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacpp.avcodec.*;

import java.util.HashMap;

import static org.bytedeco.javacpp.avcodec.*;

public class FFmpegProfile extends ConfigBag {
    private final Logger logger = LogManager.getLogger(FFmpegProfile.class);

    private final String generalConf = "g.conf.";
    private final String videoConf = "v.conf.";
    private final String audioConf = "a.conf.";

    private final String videoEncode = "v.e.";
    private final String videoDecode = "v.d.";
    private final String audioEncode = "a.e.";
    private final String audioDecode = "a.d.";

    private final String failsafeVideoCodec = "h264";
    private final String failsafeAudioCodec = "aac";

    private boolean alwaysReload;
    private String friendlyName;
    private boolean videoDisabled;
    private boolean audioDisabled;

    private boolean interlacedOnly;
    private boolean interlaceDetect;
    private boolean videoAlwaysTranscode;
    private boolean audioAlwaysTranscode;

    private String videoDefaultCodec;
    private String audioDefaultCodec;

    private HashMap<String, String> videoTranscodeMap;
    private HashMap<String, String> videoEncoderMap;
    private HashMap<String, String> videoDecoderMap;

    private HashMap<String, String> audioTranscodeMap;
    private HashMap<String, String> audioEncoderMap;
    private HashMap<String, String> audioDecoderMap;

    public FFmpegProfile(String profileName) {
        super(profileName, "transcode", false);
        alwaysReload = true;
        friendlyName = profileName;
        reload();
    }

    @Override
    public synchronized boolean saveConfig() {
        logger.warn("Saving is not enabled for transcoding profiles.");
        return false;
    }

    public void reload() {
        if (!alwaysReload) {
            return;
        }

        loadConfig();

        // If this is set wrong there's a good change other things are wrong.
        alwaysReload = getBoolean(generalConf + "always_reload", true);
        friendlyName = getString(generalConf + "friendly_name", friendlyName);
        videoDisabled = getBoolean(videoConf + "t.disable", true);
        audioDisabled = getBoolean(audioConf + "t.disable", true);
        interlacedOnly = getBoolean(videoConf + "t.deinterlace_only", true);
        interlaceDetect = getBoolean(videoConf + "t.deinterlace_detect", true);
        videoAlwaysTranscode = getBoolean(videoConf + "t.always", false);
        audioAlwaysTranscode = getBoolean(audioConf + "t.always", false);

        videoDefaultCodec = getString(videoConf + "e.default_codec", "h264");
        if (!alwaysReload && avcodec_find_encoder_by_name(videoDefaultCodec) == null) {
            logger.error("'{}' is not a valid video codec. Using the default '{}'.",
                    videoDefaultCodec, failsafeVideoCodec);
            videoDefaultCodec =  failsafeVideoCodec;
        }

        audioDefaultCodec = getString(audioConf + "e.default_codec", "aac");
        if (!alwaysReload && avcodec_find_encoder_by_name(audioDefaultCodec) == null) {
            logger.error("'{}' is not a valid audio codec. Using the default '{}'.",
                    audioDefaultCodec, failsafeAudioCodec);
            audioDefaultCodec = failsafeAudioCodec;
        }

        // This should be marginally more efficient than multiple calls.
        HashMap<String, String>[] maps = getAllByRootKey(
                videoConf + "t.codec_map.",
                videoConf + "d.codec_map.",
                videoConf + "e.codec_map.",
                audioConf + "t.codec_map.",
                audioConf + "d.codec_map.",
                audioConf + "e.codec_map.");

        videoTranscodeMap = maps[0];
        videoDecoderMap = maps[1];
        videoEncoderMap = maps[2];

        audioTranscodeMap = maps[3];
        audioDecoderMap = maps[4];
        audioEncoderMap = maps[5];
    }

    public boolean canInterlaceDetect() {
        return interlaceDetect;
    }

    public boolean canTranscodeVideo(boolean interlaced, String decoderCodec) {
        return videoAlwaysTranscode ||
                videoTranscodeMap.get(decoderCodec) != null ||
                (interlacedOnly && interlaced);
    }

    public AVCodec getVideoEncoderCodec(AVCodec decoderCodec) {
        // Get the default codec for the desired destination.
        String encoderCodec = videoTranscodeMap.get(avcodec_get_name(decoderCodec.id()).getString());

        if (encoderCodec == null) {
            encoderCodec = videoDefaultCodec;
        }

        // Get the non-default codec if there is a mapping to it.
        String preferedCodec = videoEncoderMap.get(encoderCodec);

        AVCodec returnCodec = null;

        // First, if the codec is to be remapped, try the non-default preferred codec.
        if (preferedCodec != null) {
            returnCodec = avcodec_find_decoder_by_name(preferedCodec);
        }

        // Second, if there isn't a preferred codec or the preferred codec doesn't exist, try the
        // default.
        if (returnCodec == null) {
            if (preferedCodec != null) {
                logger.error("'{}' is not a valid video codec. Replacing with the default '{}'.",
                        preferedCodec, videoDefaultCodec);
            }

            returnCodec = avcodec_find_decoder_by_name(encoderCodec);
        }

        // Third, if the default fails, a codec that is known to always be available is used.
        if (returnCodec == null) {
            logger.error("'{}' is not a valid video codec. Replacing with the failsafe '{}'.",
                    encoderCodec, failsafeVideoCodec);

            returnCodec = avcodec_find_decoder_by_name(failsafeVideoCodec);
        }

        // A null value can still potentially be returned, so it should be checked for on returns
        // from this method.
        return returnCodec;
    }

    /**
     * Get all of the encoder settings based on the width, height and codec being used for encoding.
     * <p/>
     * This merges all settings.
     *
     * @param w The width in pixels of the incoming video.
     * @param h The height in pixels of the incoming video.
     * @param encoder The codec to be used.
     * @return A HashMap containing all available properties for the provided configuration.
     */
    public HashMap<String, String> getVideoEncoderMap(int w, int h, AVCodec encoder) {
        String width = "w" + String.valueOf(w);
        String height = "h" + String.valueOf(h);
        String encoderName = avcodec_get_name(encoder.id()).getString();

        return mergeAllByRootKeys(
                videoEncode + "default." + encoderName + ".",
                videoEncode + "w*." + height + "." + encoderName + ".",
                videoEncode + width + ".h*." + encoderName + ".",
                videoEncode + width + "." + height + "." + encoderName + ".");
    }

}
