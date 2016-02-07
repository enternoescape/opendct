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

public class MessageContainerImpl implements MessageContainer {
    private final String sender;
    private final MessageSeverity severity;
    private final String message;
    private Date dates[];

    public MessageContainerImpl(String sender, MessageSeverity severity, String message, Date... dates) {
        this.sender = sender;
        this.severity = severity;
        this.message = message;
        this.dates = dates;

        if (dates.length == 0) {
            incrementRepeat();
        }
    }

    @Override
    public String getSender() {
        return sender;
    }

    @Override
    public MessageSeverity getSeverity() {
        return severity;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Date[] getTime() {
        return dates;
    }

    @Override
    public synchronized void incrementRepeat() {
        Date newDate = new Date(System.currentTimeMillis());

        Date newDates[] = new Date[dates.length + 1];

        System.arraycopy(dates, 0, newDates, 0, dates.length);
        newDates[dates.length] = newDate;

        dates = newDates;
    }

    @Override
    public int getRepeat() {
        return dates.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessageContainerImpl that = (MessageContainerImpl) o;

        if (severity != that.severity) return false;
        return message.equals(that.message);

    }

    @Override
    public int hashCode() {
        int result = severity.hashCode();
        result = 31 * result + message.hashCode();
        return result;
    }
}
