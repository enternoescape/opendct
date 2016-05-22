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

package opendct.video.java;

import opendct.config.Config;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoUtil {
    private static final Logger logger = LogManager.getLogger(VideoUtil.class);

    public static final int MTS_SYNC_BYTE = 0x47;
    public static final int MTS_PACKET_LEN = 188;

    /**
     * Returns the index of the first a TS sync byte.
     * <p/>
     * According to the standard finding a valid sync byte is done by locating a byte with the value
     * 0x47 and then checking if the sync byte can be found two more times in the right places. The
     * index is relative to the beginning of the array.
     *
     * @param packet This is the byte array to be processed.
     * @param offset This if the offset of the actual data to be processed.
     * @param length This is the length of data to be processed from the provided offset, it must be
     *               at least 752 bytes (4 188 byte TS packets).
     * @return The index value of the first located sync byte or -1 if a sync byte was not found.
     */
    public static int getTsSyncByte(byte packet[], int offset, int length) {
        int returnValue = -1;

        if (length < 752) {
            return returnValue;
        }

        int limit = offset + length;
        for (int i = offset; i < limit; i++) {
            if (packet[i] == MTS_SYNC_BYTE) {
                if (i + (MTS_PACKET_LEN * 2) < limit) {
                    byte sync1 = packet[i + MTS_PACKET_LEN];
                    byte sync2 = packet[i + (MTS_PACKET_LEN * 2)];

                    if (sync1 == MTS_SYNC_BYTE && sync2 == MTS_SYNC_BYTE) {
                        returnValue = i;
                        break;
                    }
                } else {
                    // If this is not true now, it won't be later either, so we should just return
                    // that we didn't find it.
                    break;
                }
            }
        }

        return returnValue;
    }

    /**
     * Get the index of the sync byte of the first detected start packet.
     * <p/>
     * The index is relative to the beginning of the array.
     *
     * @param packet This is the byte array to be processed.
     * @param offset This if the offset of the actual data to be processed.
     * @param length This is the length of data to be processed from the provided offset, it must be
     *               at least 752 bytes (4 188 byte TS packets).
     * @param synced <i>true</i> if the offset matches the beginning of a packet and detection does
     *               not need to be done.
     * @return The index value of the first located packet start byte or -1 if a packet start byte
     *         was not found.
     */
    public static int getTsStartSyncByte(byte packet[], int offset, int length, boolean synced) {

        int returnByte = -1;
        int firstSyncByte = offset;

        if (!synced) {
            firstSyncByte = getTsSyncByte(packet, offset, length);
        }

        if (firstSyncByte < 0) {
            return returnByte;
        }

        int limit = offset + length;
        for (int i = firstSyncByte; i < limit; i += MTS_PACKET_LEN) {
            if (i + 1 > length) {
                break;
            }
            int tsPayloadUnitStartIndicator = packet[i + 1] & 0x40;

            if (tsPayloadUnitStartIndicator != 0) {
                returnByte = i;
                break;
            }
        }

        return returnByte;
    }

    /**
     * Get the index of the sync byte of the first detected PAT packet.
     * <p/>
     * The index is relative to the beginning of the array.
     *
     * @param packet This is the byte array to be processed.
     * @param offset This if the offset of the actual data to be processed.
     * @param length This is the length of data to be processed from the provided offset, it must be
     *               at least 752 bytes (4 188 byte TS packets).
     * @param synced <i>true</i> if the offset matches the beginning of a packet and detection does
     *               not need to be done.
     * @return The index value of the first located PAT start byte or -1 if a PAT start byte was not
     *         found.
     */
    public static int getTsVideoPatStartByte(byte packet[], int offset, int length, boolean synced) {
        int returnByte = -1;
        int packetOffset;
        int currentSyncByte = getTsStartSyncByte(packet, offset, length, synced);

        if (currentSyncByte < 0) {
            return returnByte;
        }

        int limit = offset + length;
        while (currentSyncByte > -1) {
            boolean skip = false;

            if (currentSyncByte + MTS_PACKET_LEN > limit) {
                break;
            }

            if ((packet[currentSyncByte + 3] & 0x10) == 0) {
                logger.debug("getTsVideoPatStartByte: No TS payload.");
                skip = true;
            } else if ((packet[currentSyncByte + 1] & 0x80) != 0) {
                logger.debug("getTsVideoPatStartByte: Demod bad TS packet.");
                skip = true;
            }

            if (!skip) {
                int tsPacketID = ((packet[currentSyncByte + 1] & 0x1f) << 8) | ((packet[currentSyncByte + 2]) & 0xff);
                if (tsPacketID == 0) {
                    logger.debug("PMT packet found at index {}.", currentSyncByte);
                    returnByte = currentSyncByte;
                    break;
                }
            }

            packetOffset = currentSyncByte + MTS_PACKET_LEN;
            if (currentSyncByte < limit) {
                currentSyncByte = getTsStartSyncByte(packet, packetOffset, length, true);
            } else {
                break;
            }
        }

        return returnByte;
    }


    /**
     * Returns the index of the first a TS sync byte.
     * <p/>
     * According to the standard finding a valid sync byte is done by locating a byte with the value
     * 0x47 and then checking if the sync byte can be found two more times in the right places. This
     * will not increment the position of the buffer. The index is relative to the beginning of the
     * buffer.
     *
     * @param packet This is the ByteBuffer to be processed.
     * @return The index value of the first located sync byte or -1 if a sync byte was not found.
     */
    public static int getTsSyncByte(ByteBuffer packet) {
        int returnValue = -1;

        if (packet.remaining() < 752) {
            return returnValue;
        }

        int limit = packet.limit();
        for (int i = packet.position(); i < limit; i++) {
            if (packet.get(i) == MTS_SYNC_BYTE) {
                if (i + (MTS_PACKET_LEN * 2) < limit) {
                    byte sync1 = packet.get(i + MTS_PACKET_LEN);
                    byte sync2 = packet.get(i + (MTS_PACKET_LEN * 2));

                    if (sync1 == MTS_SYNC_BYTE && sync2 == MTS_SYNC_BYTE) {
                        returnValue = i;
                        break;
                    }
                } else {
                    // If this is not true now, it won't be later either, so we should just return
                    // that we didn't find it.
                    break;
                }
            }
        }

        return returnValue;

    }

    /**
     * Get the index of the sync byte of the first detected start packet.
     * <p/>
     * This will search the buffer without actually incrementing the position of the buffer. The
     * index is relative to the beginning of the buffer.
     *
     * @param packet This is the ByteBuffer to be processed.
     * @param synced <i>true</i> if the current position matches the beginning of a packet and
     *               detection does not need to be done.
     * @return The index value of the first located packet start byte or -1 if a packet start byte
     *         was not found.
     */
    public static int getTsStartSyncByte(ByteBuffer packet, boolean synced) {

        int returnByte = -1;
        int firstSyncByte = packet.position();

        if (!synced) {
            firstSyncByte = getTsSyncByte(packet);
        }

        if (firstSyncByte < 0) {
            return returnByte;
        }

        int limit = packet.remaining();
        for (int i = firstSyncByte; i < limit; i += MTS_PACKET_LEN) {
            if (i + 1 > packet.remaining()) {
                break;
            }
            int tsPayloadUnitStartIndicator = packet.get(i + 1) & 0x40;

            if (tsPayloadUnitStartIndicator != 0) {
                returnByte = i;
                break;
            }
        }

        return returnByte;
    }

    /**
     * Get the index of the sync byte of the first detected PAT packet.
     * <p/>
     * This will search the buffer without actually incrementing the position of the buffer. The
     * index is relative to the beginning of the buffer.
     *
     * @param packet This is the ByteBuffer to be processed.
     * @param synced <i>true</i> if the offset matches the beginning of a packet and detection does
     *               not need to be done.
     * @return The index value of the first located PAT start byte or -1 if a PAT start byte was not
     *         found.
     */
    public static int getTsVideoPatStartByte(ByteBuffer packet, boolean synced) {
        int returnByte = -1;
        int packetOffset;
        int currentSyncByte = getTsStartSyncByte(packet, synced);

        if (currentSyncByte < 0) {
            return returnByte;
        }

        int limit = packet.limit();
        while (currentSyncByte > -1) {
            boolean skip = false;

            if (currentSyncByte + MTS_PACKET_LEN > limit) {
                break;
            }

            if ((packet.get(currentSyncByte + 3) & 0x10) == 0) {
                logger.debug("getTsVideoPatStartByte: No TS payload.");
                skip = true;
            } else if ((packet.get(currentSyncByte + 1) & 0x80) != 0) {
                logger.debug("getTsVideoPatStartByte: Demod bad TS packet.");
                skip = true;
            }

            if (!skip) {
                int tsPacketID = ((packet.get(currentSyncByte + 1) & 0x1f) << 8) | ((packet.get(currentSyncByte + 2) & 0xff));
                if (tsPacketID == 0) {
                    logger.debug("PAT packet found at index {}.", currentSyncByte);
                    returnByte = currentSyncByte;
                    break;
                }
            }

            if (currentSyncByte < limit) {
                ByteBuffer duplicate = packet.duplicate();
                packetOffset = currentSyncByte + MTS_PACKET_LEN;
                duplicate.position(packetOffset);

                currentSyncByte = getTsStartSyncByte(duplicate, true);
            } else {
                break;
            }
        }

        return returnByte;
    }

    /**
     * Get the index of the sync byte of the first detected PES I frame packet.
     * <p/>
     * This will search the buffer without actually incrementing the position of the buffer. The
     * index is relative to the beginning of the buffer.
     *
     * @param packet This is the ByteBuffer to be processed.
     * @param synced <i>true</i> if the offset matches the beginning of a packet and detection does
     *               not need to be done.
     * @return The index value of the first located PES start byte or -1 if a PES start byte was not
     *         found.
     */
    public static int getTsVideoPesStartByte(ByteBuffer packet, boolean synced) {
        int returnByte = -1;
        int packetOffset;
        int currentSyncByte = getTsStartSyncByte(packet, synced);

        if (currentSyncByte < 0) {
            return returnByte;
        }

        int limit = packet.limit();
        while (currentSyncByte > -1) {
            boolean skip = false;

            if (currentSyncByte + MTS_PACKET_LEN > limit) {
                break;
            }

            if ((packet.get(currentSyncByte + 3) & 0x10) == 0) {
                logger.debug("getTsVideoPatStartByte: No TS payload.");
                skip = true;
            } else if ((packet.get(currentSyncByte + 1) & 0x80) != 0) {
                logger.debug("getTsVideoPatStartByte: Demod bad TS packet.");
                skip = true;
            }

            if (!skip) {
                int tsPacketID = ((packet.get(currentSyncByte + 1) & 0x1f) << 8) | ((packet.get(currentSyncByte + 2) & 0xff));
                if (tsPacketID == 0) {
                    logger.debug("PAT packet found at index {}.", currentSyncByte);
                } else {
                    for (int i = currentSyncByte; i < currentSyncByte + MTS_PACKET_LEN - 5; i++) {
                        if (packet.get(i) == 0x00 &&
                                packet.get(i + 1) == 0x00 &&
                                packet.get(i + 2) == 0x00 &&
                                packet.get(i + 3) == 0x01) {

                            return currentSyncByte;
                        }
                    }
                }
            }

            if (currentSyncByte < limit) {
                ByteBuffer duplicate = packet.duplicate();
                packetOffset = currentSyncByte + MTS_PACKET_LEN;
                duplicate.position(packetOffset);

                currentSyncByte = getTsStartSyncByte(duplicate, true);
            } else {
                break;
            }
        }

        return returnByte;
    }

    public static int getTsVideoTransition(ByteBuffer packet, boolean synced) {
        int returnByte = -1;
        int packetOffset;
        int videoPacketID = -1;
        int currentSyncByte = getTsStartSyncByte(packet, synced);

        if (currentSyncByte < 0) {
            return returnByte;
        }

        int limit = packet.limit();
        while (currentSyncByte > -1) {
            boolean skip = false;

            if (currentSyncByte + MTS_PACKET_LEN > limit) {
                break;
            }

            if ((packet.get(currentSyncByte + 3) & 0x10) == 0) {
                logger.debug("getTsVideoPatStartByte: No TS payload.");
                skip = true;
            } else if ((packet.get(currentSyncByte + 1) & 0x80) != 0) {
                logger.debug("getTsVideoPatStartByte: Demod bad TS packet.");
                skip = true;
            }

            if (!skip) {
                int tsPacketID = ((packet.get(currentSyncByte + 1) & 0x1f) << 8) | ((packet.get(currentSyncByte + 2) & 0xff));

                if (tsPacketID == 0) {
                    logger.debug("PAT packet found at index {}.", currentSyncByte);
                } else if (videoPacketID == -1 || videoPacketID == tsPacketID) {
                    boolean searchPacket = false;

                    for (int i = currentSyncByte; i < currentSyncByte + MTS_PACKET_LEN - 5; i++) {
                        if (!searchPacket) {
                            if (packet.get(i) == 0x00 &&
                                    packet.get(i + 1) == 0x00 &&
                                    packet.get(i + 2) == 0x00 &&
                                    packet.get(i + 3) == 0x01) {

                                //logger.debug("PES packet found at index {}, packet ID {}.", currentSyncByte, tsPacketID);
                                videoPacketID = tsPacketID;
                                searchPacket = true;
                            }
                        } else {
                            if (packet.get(i) == 0x00 &&
                                    packet.get(i + 1) == 0x00 &&
                                    packet.get(i + 2) == 0x01 &&
                                    packet.get(i + 3) == 0xBA) {

                                logger.debug("MPEG2 packet found at index {}, packet ID {}.", currentSyncByte, tsPacketID);
                                videoPacketID = tsPacketID;
                            }
                        }
                    }
                }
            }

            if (currentSyncByte < limit) {
                ByteBuffer duplicate = packet.duplicate();
                packetOffset = currentSyncByte + MTS_PACKET_LEN;
                duplicate.position(packetOffset);

                currentSyncByte = getTsStartSyncByte(duplicate, true);
            } else {
                break;
            }
        }

        return returnByte;
    }

    /**
     * Get the index of the first random access indicator.
     * <p/>
     * This will search the buffer without actually incrementing the position of the buffer. The
     * index is relative to the beginning of the buffer.
     *
     * @param packet This is the ByteBuffer to be processed.
     * @param synced <i>true</i> if the offset matches the beginning of a packet and detection does
     *               not need to be done.
     * @return The index value of the first packet containing a random access indicator or -1 if
     *         the packet was not found.
     */
    public static int getTsVideoRandomAccessIndicator(ByteBuffer packet, boolean synced) {
        int returnByte = -1;
        int packetOffset;
        int currentSyncByte = getTsStartSyncByte(packet, synced);

        if (currentSyncByte < 0) {
            return returnByte;
        }

        int limit = packet.limit();
        while (currentSyncByte > -1) {
            boolean skip = false;

            if (currentSyncByte + MTS_PACKET_LEN > limit) {
                break;
            }

            if ((packet.get(currentSyncByte + 3) & 0x10) == 0) {
                logger.debug("getTsVideoPatStartByte: No TS payload.");
                skip = true;
            } else if ((packet.get(currentSyncByte + 1) & 0x80) != 0) {
                logger.debug("getTsVideoPatStartByte: Demod bad TS packet.");
                skip = true;
            }

            if (!skip) {
                int adaptationField = packet.get(currentSyncByte + 3) & 0x20;

                if (adaptationField > 0) {
                    int adapatationFieldLength = packet.get(currentSyncByte + 4) & 0xff;
                    if (adapatationFieldLength > 0) {
                        logger.info("Adaptation field present. length = {}", adapatationFieldLength);
                        int randomAccessIndicator = packet.get(currentSyncByte + 5) & 0x40;
                        if (randomAccessIndicator > 0) {
                            logger.info("Random Access Indicator present.");
                            return currentSyncByte;
                        }
                    }
                }
            }

            if (currentSyncByte < limit) {
                ByteBuffer duplicate = packet.duplicate();
                packetOffset = currentSyncByte + MTS_PACKET_LEN;
                duplicate.position(packetOffset);

                currentSyncByte = getTsStartSyncByte(duplicate, true);
            } else {
                break;
            }
        }

        return returnByte;
    }

    public static File COPY_ONCE_TS = new File(Config.VID_DIR + "CopyOnce.ts");
    public static long writeCopyOnceTs(String filepath) throws IOException {
        Util.copyFile(COPY_ONCE_TS, new File(filepath), true);
        return COPY_ONCE_TS.length();
    }

    public static File COPY_NEVER_TS = new File(Config.VID_DIR + "CopyNever.ts");
    public static long writeCopyNeverTs(String filepath) throws IOException {
        Util.copyFile(COPY_NEVER_TS, new File(filepath), true);
        return COPY_NEVER_TS.length();
    }
}
