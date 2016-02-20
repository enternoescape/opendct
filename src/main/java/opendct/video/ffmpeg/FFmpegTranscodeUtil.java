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

import static org.bytedeco.javacpp.avcodec.AVCodecContext;
import static org.bytedeco.javacpp.avutil.AVDictionary;
import static org.bytedeco.javacpp.avutil.av_dict_set;


public class FFmpegTranscodeUtil {
    public static void setH264(H264Preset preset, AVCodecContext enc_ctx, AVDictionary dict) {
        av_dict_set(dict, "profile", "high", 0);
        av_dict_set(dict, "level", "4.0", 0);
        av_dict_set(dict, "preset", "veryfast", 0);
        av_dict_set(dict, "tune", "fastdecode,zerolatency", 0);
        av_dict_set(dict, "crf", "23", 0);

        enc_ctx.bit_rate(14 * 1024 * 1024);
        enc_ctx.me_cmp(1);
        enc_ctx.me_range(16);
        enc_ctx.qmin(15);
        enc_ctx.qmax(30);
    }
}
