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

package opendct.producer;

import opendct.consumer.SageTVConsumer;

import java.io.IOException;

/**
 * This provides the very basics to get a producer to be used by a SageTVConsumer interface.
 */
public interface SageTVProducer extends Runnable {

    /**
     * Is the thread currently running?
     *
     * @return <i>true</i> if the thread is running.
     */
    public boolean getIsRunning();

    /**
     * Set the producer.
     * <p/>
     * You must include a pointer to the consumer interface or you will
     * have nowhere to write your data. You must set the consumer or
     * there will be no output.
     *
     * @param sageTVConsumer The already configured and running consumer.
     */
    public void setConsumer(SageTVConsumer sageTVConsumer) throws IOException;

    /**
     * Returns the number of packets that needed to be dropped.
     * <p/>
     * Packets can be dropped because the queue was too full or device/transport issues. Packets is
     * a very generalized word here and the definition will vary by producer. For example, it could
     * just reflect the number of errors encountered. This is used for statistical analysis and this
     * should not be getting called often.
     *
     * @return
     */
    public int getPacketsLost();

    /**
     * Stop anything that an interrupt will not stop in the producer as quickly as possible.
     */
    public void stopProducing();
}
