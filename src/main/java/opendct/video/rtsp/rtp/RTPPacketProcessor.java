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

package opendct.video.rtsp.rtp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RTPPacketProcessor {
    private final Logger logger = LogManager.getLogger(RTPPacketProcessor.class);
    private static final ReentrantReadWriteLock statisticLock = new ReentrantReadWriteLock();
    private volatile int missedRTPPackets = 0;

    // These are really 8-bit values, but since we can't make it unsigned,
    // that's not an option. Also the actual number is two bytes, but the
    // least significant byte is enough since I doubt we will skip exactly
    // 255 packets and cause the checks to not detect the dropped packets.
    int currentRTPPacket = 0;
    int lastRTPPacket = -1;

    boolean unexpectedRTPPacket;
    boolean rollover;
    int protocol;
    int timeStamp;
    int ssrc;

    public void ResetCounters() {
        statisticLock.writeLock().lock();
        try {
            missedRTPPackets = 0;
            currentRTPPacket = 0;
            lastRTPPacket = -1;
        } finally {
            statisticLock.writeLock().unlock();
        }
    }

    /*
    The first 12 bytes are RTP header data and do not need to be written out to the file.

    Bytes 1-2 (index 0-1) is protocol information set in bits.
    In our situation, these can be stripped away without issue.

    Bytes 3-4 (index 2-3) is incremented for each new packet.
    We can track missed packets this way. I'm just using the last byte since it's all the same.

    Bytes 5-8 (index 4-7) is a timestamp.
    This is used to correct jitter. Since our goal is to move data quickly and not play it
    back here, this doesn't really matter to us.

    Bytes 9-12 (index 8-11) is the SSRC field. It is used to identify the synchronization source.
    We could use this to ensure we are reading all the same stream after a few packets with this
    same value, we can start to filter out unrelated data if needed.

    Byte 13-16 (index 12-15) should be a 32-bit CSRC list of sources.

    Source: Wikipedia
    */

    /**
     * Reads an RTP packet and determines if we have missed any packets.
     * <p/>
     * This method also increments a counter every time we are out of sync.
     *
     * @param datagramPacket The RTP datagram bytes.
     * @return <i>true</i> if there are no problems with the synchronicity.
     */
    public boolean findMissingRTPPackets(byte[] datagramPacket) {
        logger.entry();

        unexpectedRTPPacket = false;

        statisticLock.writeLock().lock();

        try {
            currentRTPPacket = datagramPacket[3] & 0xff;

            if (lastRTPPacket == 255 && currentRTPPacket != 0) {
                logger.warn("Expected frame number {}, received frame number {}", 0, currentRTPPacket);
                missedRTPPackets++;
                unexpectedRTPPacket = true;
            } else if (lastRTPPacket != -1 && lastRTPPacket != 255) {
                if ((lastRTPPacket + 1) != currentRTPPacket) {
                    logger.warn("Expected frame number {}, received frame number {}", (lastRTPPacket + 1), currentRTPPacket);
                    missedRTPPackets++;
                    unexpectedRTPPacket = true;
                }
            }

            lastRTPPacket = currentRTPPacket;
        } finally {
            statisticLock.writeLock().unlock();
        }

        return logger.exit(unexpectedRTPPacket);
    }

    public boolean findMissingRTPPackets(ByteBuffer datagramPacket) {
        logger.entry();

        unexpectedRTPPacket = false;

        statisticLock.writeLock().lock();

        try {
            //protocol = datagramPacket.getShort() & 0xffff;
            datagramPacket.position(datagramPacket.position() + 2);
            currentRTPPacket = datagramPacket.getShort() & 0xffff;
            datagramPacket.position(datagramPacket.position() + 8);
            //timeStamp = datagramPacket.getInt();
            //ssrc = datagramPacket.getInt();
            //ssrcList = datagramPacket.getInt();

            // datagramPacket will return with the read index at
            // 12 which is where we want it.

            //65535
            if (lastRTPPacket != -1) {
                rollover = false;

                if (lastRTPPacket == 65535 && currentRTPPacket == 0) {
                    rollover = true;
                } else if (lastRTPPacket == 65535) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Expected frame number {}, got {}", 0, currentRTPPacket);
                    }
                    rollover = true;
                }

                if (!rollover && (lastRTPPacket + 1) != currentRTPPacket) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Expected frame number {}, got {}", (lastRTPPacket + 1), currentRTPPacket);
                    }
                    missedRTPPackets++;
                    unexpectedRTPPacket = true;
                }
            }

            lastRTPPacket = currentRTPPacket;
        } finally {
            statisticLock.writeLock().unlock();
        }

        return logger.exit(unexpectedRTPPacket);
    }

    public int getMissedRTPPackets() {
        logger.entry();
        int returnValue = 0;

        statisticLock.readLock().lock();

        try {
            returnValue = missedRTPPackets;
        } finally {
            statisticLock.readLock().unlock();
        }

        return logger.exit(returnValue);
    }
}
