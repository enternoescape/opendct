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

import java.io.IOException;
import java.nio.ByteBuffer;

public interface FFmpegWriter {

    /**
     * This is how the FFmpegContext is able to write the data it has muxed.
     * <p/>
     * Return -1 to tell FFmpegContext to stop writing.
     *
     * @param data The data to be written.
     * @param file This number is an index that corresponds with the file to be written. This allows
     *             the callback to output to multiple files or to switch to a new file.
     * @return The number of bytes written. This should match the limit on the data to be written.
     * @throws IOException
     */
    public int write(ByteBuffer data, int file) throws IOException;
}
