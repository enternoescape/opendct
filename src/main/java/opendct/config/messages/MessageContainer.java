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

package opendct.config.messages;

import org.apache.logging.log4j.Level;

public interface MessageContainer {

    /**
     * The class name of the sender.
     * <p/>
     * Do not keep a reference to the actual object that generated this message.
     *
     * @return The class name of the sender.
     */
    public String getSender();

    /**
     * The severity level of this message.
     *
     * @return The severity level of this message.
     */
    public Level getLevel();

    /**
     * A static title for this error type.
     *
     *
     * @return The title for this error type.
     */
    public String getTitle();

    /**
     * The actual message.
     *
     * @return The actual message.
     */
    public String getMessage();

    /**
     * The date and time of every occurrence.
     *
     * @return The date and time of the first occurrence in Epoch.
     */
    public long[] getTime();

    /**
     * This actually puts the message into the log.
     *
     * @param exception The exception if an exception is to be logged. This value can be
     *                  <i>null</i>.
     */
    public void logMessage(Exception exception);

    /**
     * Increment the repeat count for this message.
     * <p/>
     * This method is called when a message is determined to the exact same message as a newly
     * submitted message.
     */
    public void incrementRepeat();

    /**
     * The number of times this message has been recorded.
     *
     * @return The number of times this message has been recorded.
     */
    public int getRepeat();
}
