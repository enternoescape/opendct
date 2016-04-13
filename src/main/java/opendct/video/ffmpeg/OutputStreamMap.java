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

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avformat;
import org.bytedeco.javacpp.avutil;

import static opendct.video.ffmpeg.FFmpegUtil.NO_STREAM_IDX;

public class OutputStreamMap {
    protected int outStreamIndex;
    protected avcodec.AVCodec iCodec;
    protected avutil.AVDictionary iDict;
    protected avformat.AVStream iStream;
    protected avformat.AVStream oStream;
    protected avcodec.AVCodecContext iCodecContext;
    protected avcodec.AVCodecContext oCodecContext;
    protected int iCodecType;

    protected avutil.AVRational iStreamRational;
    protected avutil.AVRational iCodecRational;
    protected avutil.AVRational oStreamRational;
    protected avutil.AVRational oCodecRational;

    protected boolean transcode;

    public OutputStreamMap() {
        outStreamIndex = NO_STREAM_IDX;
        iCodecType = -1;
        transcode = false;
    }

    public int getOutStreamIndex() {
        return outStreamIndex;
    }

    public avcodec.AVCodec getiCodec() {
        return iCodec;
    }

    public avutil.AVDictionary getiDict() {
        return iDict;
    }

    public boolean isTranscode() {
        return transcode;
    }

    public avformat.AVStream getiStream() {
        return iStream;
    }

    public avcodec.AVCodecContext getiCodecContext() {
        return iCodecContext;
    }

    public int getiCodecType() {
        return iCodecType;
    }
}
