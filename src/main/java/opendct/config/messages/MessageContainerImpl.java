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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MessageContainerImpl implements MessageContainer {
    private final String sender;
    private final Level level;
    private final String title;
    private final String message;
    private long dates[];

    public MessageContainerImpl(String sender, Level level, String title, String message, long... dates) {
        this.sender = sender;
        this.level = level;
        this.title = title;
        this.message = message;
        this.dates = dates;

        if (dates.length == 0) {
            incrementRepeat();
        }
    }

    public MessageContainerImpl(String sender, Level level, MessageTitle title, String message, long... dates) {
        this.sender = sender;
        this.level = level;
        this.title = title.TITLE;
        this.message = message;
        this.dates = dates;

        if (dates.length == 0) {
            incrementRepeat();
        }
    }

    /**
     * This actually puts the message into the log.
     *
     * @param exception The exception if an exception is to be logged. This value can be
     *                  <i>null</i>.
     */
    public void logMessage(Exception exception) {
        final Logger logger = LogManager.getLogger(sender);

        if (exception != null) {
            logger.log(level, "{} => ", message, exception);
        } else {
            logger.log(level, message);
        }
    }

    public String getSender() {
        return sender;
    }

    public Level getLevel() {
        return level;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public long[] getTime() {
        return dates;
    }

    public synchronized void incrementRepeat() {
        long newDate = System.currentTimeMillis();

        long newDates[] = new long[dates.length + 1];

        System.arraycopy(dates, 0, newDates, 0, dates.length);
        newDates[dates.length] = newDate;

        dates = newDates;
    }

    public int getRepeat() {
        return dates.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessageContainerImpl that = (MessageContainerImpl) o;

        if (!level.equals(that.level)) return false;
        if (!title.equals(that.title)) return false;
        return message.equals(that.message);

    }

    @Override
    public int hashCode() {
        int result = level.hashCode();
        result = 31 * result + title.hashCode();
        result = 31 * result + message.hashCode();
        return result;
    }
}
