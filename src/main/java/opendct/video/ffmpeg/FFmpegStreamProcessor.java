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

public interface FFmpegStreamProcessor {

    /**
     * Set up everything needed to start streaming and write the header to the file.
     *
     * @param ctx The context already populated with incoming stream info from stream detection.
     * @param outputFilename The filename or a filename with an extension matching the desired
     *                       output container.
     * @param writer A writer implementation to be used for writing the results of the FFmpeg
     *               transcoding/remuxing.
     * @throws FFmpegException Thrown if there is a problem initializing. This exception will not
     *                         de-allocate anything provided by stream detection.
     * @throws InterruptedException If the thread is interrupted. This exception will not
     *                              de-allocate anything provided by stream detection.
     */
    public void initStreamOutput(FFmpegContext ctx, String outputFilename, FFmpegWriter writer)
            throws FFmpegException, InterruptedException;

    /**
     * Start transcoding/remuxing to the provided writer.
     *
     * @throws FFmpegException Thrown if there is an impassible problem transcoding/remuxing. This
     *                         exception will not de-allocate anything provided by stream detection.
     */
    public void streamOutput() throws FFmpegException;

    /**
     * Switch to another writer on the next key frame.
     * <p/>
     * This method blocks until the transition has occurred or 10 seconds has passed. After 10
     * seconds, the transition is forced even if it's not an ideal slice point.
     *
     * @param newFilename The filename or a filename with an extension matching the desired
     *                    output container. You can actually change containers mid-stream if
     *                    desired.
     * @param writer A new writer implementation to be used for writing the results of the FFmpeg
     *               transcoding/remuxing. The old writer will be removed from the HashMap
     *               automatically.
     * @return <i>true</i> if the transition was successful.
     * @throws FFmpegException Thrown if there is an impassible problem re-initializing. This
     *                         exception will not de-allocate anything provided by stream detection.
     */
    public boolean switchOutput(String newFilename, FFmpegWriter writer) throws FFmpegException;
}
