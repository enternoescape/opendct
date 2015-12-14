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

package opendct.consumer;

import java.io.IOException;
import java.net.InetAddress;

public interface SageTVConsumer extends Runnable {
/**
 * The consumer thread is responsible for processing data and transferring it to SageTV via direct
 * file writing, uploadID or both. The consumer provides its own buffer. This allows the consumer to
 * be much more flexible in how it prefers to receive data. Currently MPEG-TS is the only expected
 * data format to be placed in the consumer buffer. The producer should ensure that this is the
 * format of the data provided to the consumer.
 */

    /**
     * This is the starting method of the consuming thread.
     */
    public void run();

    /**
     * Copy as many bytes as possible from the producer into a provided byte array.
     * <p/>
     * This method is needed for the producer to write it's data into the consumers buffer. This
     * allows the consumer to manage it's own buffer and reduces the number of copies. Ensure this
     * method is designed to return quickly.
     *
     * @param bytes  This is the byte array of data to be written.
     * @param offset This is the offset to start writing bytes from the array.
     * @throws IOException
     */
    public void write(byte[] bytes, int offset, int length) throws IOException;

    /**
     * Set the recording buffer size.
     * <p/>
     * It's up to the consumer to actually implement this. This is needed if you want to be able to
     * do channel previews correctly. This is the maximum size the provided file should grow to
     * before you must return to the start of the file to continue writing. Failure to circle back
     * to the beginning of the file will result in SageTV repeat the last few seconds contained
     * prior to the buffer size. This is effectively a file based circular buffer. You must continue
     * to increment the value returned by <b>getBytesStreamed()</b> even after you return to the
     * beginning of the file. If you do not, SageTV will stop playback.
     *
     * @param bufferSize The requested recording buffer size.
     */
    public void setRecordBufferSize(long bufferSize);

    /**
     * Does this consumer implement switching?
     *
     * @return Returns <i>true</i> if this implementation supports switching.
     */
    public boolean canSwitch();

    /**
     * Is the consumer currently running?
     *
     * @return Returns <i>true</i> if the consumer is still running.
     */
    public boolean getIsRunning();

    /**
     * Stop anything that an interrupt will not stop in the consumer as quickly as possible.
     */
    public void stopConsumer();

    /**
     * Tells the consumer if it should discard all consumed content.
     * <p/>
     * You still need to increment the value returned by <b>getBytesStreamed()</b> since the only
     * use for this feature so far is to detect if data would be streamed. Everything about the
     * consumer should run as usual, but all consumed content that would be streams must be
     * discarded.
     * <p/>
     * The reason this needs to be set for null output is so the consumer knows that you intended to
     * do this, otherwise it should throw an error. Also, because this is not normally the intended
     * behavior, this value should be <i>false</i> by default.
     *
     * @param consumeToNull Set this <i>true</i> to consume to a null output.
     */
    public void consumeToNull(boolean consumeToNull);

    /**
     * Get the current number of bytes streamed.
     * <p/>
     * Be sure to never increment this unless the bytes are already written to disk. This number
     * only resets to 0 on SWITCH or if the consumer is stopped/restarted. This number must be
     * thread-safe since it will be requested several times while the consumer is running.
     *
     * @return The number of bytes streamed.
     */
    public long getBytesStreamed();

    /**
     * Can this consumer record directly to a file via uploadID?
     * <p/>
     * If this is set to <i>true</i>, the <b>streamToUploadID()</b> function must actually work.
     *
     * @return <i>true</i> if this consumer can record to files via uploadID.
     */
    public boolean acceptsUploadID();

    /**
     * Can this consumer record directly to a file?
     * <p/>
     * If this is set to <i>true</i>, <b>streamToFilename()</b> must actually work.
     *
     * @return <i>true</i> if this consumer can record directly to files.
     */
    public boolean acceptsFilename();

    /**
     * Set the encoder quality.
     * <p/>
     * This value cannot be changed while the consumer is running. Maybe we can come up with
     * something creative for this like use it to set parameters for a live transcode.
     *
     * @param encodingQuality
     */
    public void setEncodingQuality(String encodingQuality);

    /**
     * Write the recording file via an uploadID through SageTV.
     *
     * @param filename The full path including the filename to record.
     * @param uploadId The SageTV provided UploadID.
     * @param socketAddress The address of the SageTV server.
     * @return <i>true</i> if the uploadID was able to open a file for recording.
     */
    public boolean consumeToUploadID(String filename, int uploadId, InetAddress socketAddress);

    /**
     * Sets and opens the file to record.
     *
     * @param filename The full path including the filename to record.
     * @return <i>true</i> if the file was able to be opened for recording.
     */
    public boolean consumeToFilename(String filename);

    /**
     * Switch the recording file via an uploadID through SageTV. Block until the uploadId is in use.
     * SageTV will also know the switch happened when the recorded bytes go back to zero.
     *
     * @param filename The full path including the filename to record.
     * @param bufferSize This is the size in bytes to record before returning to the start of the
     *                   file.
     * @param uploadId The SageTV provided UploadID.
     * @return <i>true</i> if the uploadID was able to open a file for recording.
     */
    public boolean switchStreamToUploadID(String filename, long bufferSize, int uploadId);

    /**
     * Switch the recording file to a new file. Block until the new file is being written. SageTV
     * will also know the switch happened when the recorded bytes go back to zero.
     *
     * @param filename The full path including the filename to record.
     * @param bufferSize This is the size in bytes to record before returning to the start of the
     *                   file.
     * @return <i>true</i> if the file was able to be opened for recording.
     */
    public boolean switchStreamToFilename(String filename, long bufferSize);

    /**
     * Get the current encoder quality.
     *
     * @return The encoder quality as a string.
     */
    public String getEncoderQuality();

    /**
     * Get the current file name being recorded.
     *
     * @return The full path and file name being recorded.
     */
    public String getEncoderFilename();

    /**
     * Get the current uploadID in use.
     *
     * @return The current uploadID or 0 if uploadID is not in use.
     */
    public int getEncoderUploadID();

    /**
     * Sets the desired PIDs from the originating transport stream.
     * <p/>
     * The consumer should not rely on this being present. If this value is never set or is set to
     * an empty array, the consumer must do a best effort.
     *
     * @param pids The desired PIDs.
     */
    public void setPids(int pids[]);

    /**
     * Sets the desired program from the originating transport stream.
     * <p/>
     * The consumer should not rely on this being set. If this value is never set or is set to
     * -1, the consumer must do a best effort.
     *
     * @param program The desired program number.
     */
    public void setProgram(int program);

    /**
     * Gets the desired PIDs.
     *
     * @return The desired PIDs or an empty array.
     */
    public int[] getPids();

    /**
     * Gets the desired program number.
     *
     * @return The desired program.
     */
    public int getProgram();
}
