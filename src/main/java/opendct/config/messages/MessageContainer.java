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

import java.util.Date;

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
     * The severity of this message.
     *
     * @return The severity of this message.
     */
    public MessageSeverity getSeverity();

    /**
     * The actual message.
     *
     * @return The actual message.
     */
    public String getMessage();

    /**
     * The date and time of every occurrence.
     *
     * @return The date and time of the first occurrence.
     */
    public Date[] getTime();

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
