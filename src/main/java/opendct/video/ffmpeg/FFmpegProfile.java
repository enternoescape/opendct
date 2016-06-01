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
import org.bytedeco.javacpp.avcodec.AVCodec;

import java.util.Map;

import static org.bytedeco.javacpp.avcodec.avcodec_find_encoder_by_name;
import static org.bytedeco.javacpp.avcodec.avcodec_get_name;

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
    private String description;
    private boolean profileDisabled;

    private boolean interlacedOnly;
    private boolean progressiveOnly;
    private boolean interlaceDetect;
    private boolean videoAlwaysTranscode;
    private boolean audioAlwaysTranscode;
    private int gtHeight;
    private int gtWidth;
    private int eqHeight;
    private int eqWidth;

    private String videoDefaultCodec;
    private String audioDefaultCodec;

    private Map<String, String> videoTranscodeMap;
    private Map<String, String> videoEncoderMap;
    private Map<String, String> videoDecoderMap;

    private Map<String, String> audioTranscodeMap;
    private Map<String, String> audioEncoderMap;
    private Map<String, String> audioDecoderMap;

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
        profileDisabled = getBoolean(generalConf + "disable", true);
        description = getString(generalConf + "description", friendlyName);

        gtHeight = getInteger(videoConf + "t.allow_gt_h", 0);
        gtWidth = getInteger(videoConf + "t.allow_gt_w", 0);
        eqHeight = getInteger(videoConf + "t.allow_eq_h", 0);
        eqWidth = getInteger(videoConf + "t.allow_eq_w", 0);
        interlacedOnly = getBoolean(videoConf + "t.deinterlace_only", false);
        progressiveOnly = getBoolean(videoConf + "t.progressive_only", false);
        interlaceDetect = getBoolean(videoConf + "t.deinterlace_detect", true);
        videoAlwaysTranscode = getBoolean(videoConf + "t.always", false);
        audioAlwaysTranscode = getBoolean(audioConf + "t.always", false);

        videoDefaultCodec = getString(videoConf + "e.default_codec", "libx264");
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
        Map<String, String>[] maps = getAllByRootKey(
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

    /**
     * Used by stream detection to determine if detection of interlaced content should be performed.
     *
     * @param height The height of the incoming video.
     * @param width The width of the incoming video.
     * @return <i>true</i> if it makes sense to run deinterlace detection.
     */
    public boolean canInterlaceDetect(int height, int width) {
        return !profileDisabled && interlaceDetect && gtHeight < height && gtWidth < width &&
                (eqHeight == 0 || eqHeight == height) && (eqWidth == 0 || eqWidth == width);
    }

    public boolean canTranscodeVideo(boolean interlaced, String decoderCodec, int height, int width) {
        if (profileDisabled) {
            logger.debug("canTranscodeVideo: Profile disabled." +
                    " interlaced: {}, decoderCodec: {}, height: {}, width: {}",
                    interlaced, decoderCodec, height, width);
            return false;
        }

        if (videoAlwaysTranscode) {
            logger.debug("canTranscodeVideo: Always transcode." +
                            " interlaced: {}, decoderCodec: {}, height: {}, width: {}",
                    interlaced, decoderCodec, height, width);
            return true;
        }

        if (gtHeight >= height) {
            logger.debug("canTranscodeVideo: Height is too small." +
                            " interlaced: {}, decoderCodec: {}, height: {} < {}, width: {}",
                    interlaced, decoderCodec, height, gtHeight, width);
            return false;
        }

        if (gtWidth >= width) {
            logger.debug("canTranscodeVideo: Width is too small." +
                            " interlaced: {}, decoderCodec: {}, height: {}, width: {} < {}",
                    interlaced, decoderCodec, height, width, gtWidth);
            return false;
        }

        if (eqHeight > 0 && eqHeight != height) {
            logger.debug("canTranscodeVideo: Height is not equal." +
                            " interlaced: {}, decoderCodec: {}, height: {} != {}, width: {}",
                    interlaced, decoderCodec, height, eqHeight, width);
            return false;
        }

        if (eqWidth > 0 && eqWidth != width) {
            logger.debug("canTranscodeVideo: Width is not equal." +
                            " interlaced: {}, decoderCodec: {}, height: {}, width: {} != {}",
                    interlaced, decoderCodec, height, width, eqWidth);
            return false;
        }

        String desiredCodec = videoTranscodeMap.get(decoderCodec);
        if (desiredCodec != null) {
            logger.debug("canTranscodeVideo: Codec is to be transcoded to {}." +
                            " interlaced: {}, decoderCodec: {}, height: {}, width: {}",
                    desiredCodec, interlaced, decoderCodec, height, width);
            return true;
        }

        if (interlacedOnly && interlaced) {
            logger.debug("canTranscodeVideo: Video is interlaced." +
                            " interlaced: true, decoderCodec: {}, height: {}, width: {}",
                    decoderCodec, height, width);
            return true;
        }

        if (progressiveOnly && !interlaced) {
            logger.debug("canTranscodeVideo: Video is progressive." +
                            " progressive: true, decoderCodec: {}, height: {}, width: {}",
                    decoderCodec, height, width);
            return true;
        }

        logger.debug("canTranscodeVideo: No rules matched for transcoding." +
                        " interlaced: {}, decoderCodec: {}, height: {}, width: {}",
                interlaced, decoderCodec, height, width);

        return false;
    }

    public AVCodec getVideoEncoderCodec(AVCodec decoderCodec) {
        if (decoderCodec == null) return null;

        // Get the default codec for the desired destination.
        String decoderCodecName = avcodec_get_name(decoderCodec.id()).getString();
        String encoderCodec = videoTranscodeMap.get(decoderCodecName);

        if (encoderCodec == null) {
            encoderCodec = videoDefaultCodec;
        }

        // Get the non-default codec if there is a mapping to it.
        String preferedCodec = videoEncoderMap.get(encoderCodec);

        AVCodec returnCodec = null;

        // First, if the codec is to be remapped, try the non-default preferred codec.
        if (preferedCodec != null) {
            returnCodec = avcodec_find_encoder_by_name(preferedCodec);
        }

        // Second, if there isn't a preferred codec or the preferred codec doesn't exist, try the
        // default.
        if (returnCodec == null) {
            if (preferedCodec != null) {
                logger.error("'{}' is not a valid video codec. Replacing with the default '{}'.",
                        preferedCodec, videoDefaultCodec);
            }

            returnCodec = avcodec_find_encoder_by_name(encoderCodec);
        }

        // Third, if the default fails, a codec that is known to always be available is used.
        if (returnCodec == null) {
            logger.error("'{}' is not a valid video codec. Replacing with the failsafe '{}'.",
                    encoderCodec, failsafeVideoCodec);

            returnCodec = avcodec_find_encoder_by_name(failsafeVideoCodec);
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
    public Map<String, String> getVideoEncoderMap(int w, int h, AVCodec encoder) {
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
