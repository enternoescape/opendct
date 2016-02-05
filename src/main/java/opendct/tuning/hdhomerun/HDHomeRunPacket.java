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

package opendct.tuning.hdhomerun;

import opendct.tuning.hdhomerun.types.HDHomeRunPacketTag;
import opendct.tuning.hdhomerun.types.HDHomeRunPacketType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public class HDHomeRunPacket {
    private static final Logger logger = LogManager.getLogger(HDHomeRunPacket.class);

    public final static int HDHOMERUN_DISCOVER_UDP_PORT = 65001;
    public final static int HDHOMERUN_CONTROL_TCP_PORT = 65001;

    public final static int HDHOMERUN_MAX_PACKET_SIZE = 1460;
    public final static int HDHOMERUN_MAX_PAYLOAD_SIZE = 1452;

    public final static int HDHOMERUN_DEVICE_TYPE_WILDCARD = 0xFFFFFFFF;
    public final static int HDHOMERUN_DEVICE_TYPE_TUNER = 0x00000001;
    public final static int HDHOMERUN_DEVICE_ID_WILDCARD = 0xFFFFFFFF;

    public final static int HDHOMERUN_MIN_PEEK_LENGTH = 4;

    public final ByteBuffer BUFFER;

    HDHomeRunPacket() {
        BUFFER = ByteBuffer.allocate(3074);

    }

    HDHomeRunPacket(ByteBuffer buffer) {
        BUFFER = buffer;

    }

    /**
     * Starts a new packet.
     * <p/>
     * This will clear the buffer.
     *
     * @param type
     */
    public void startPacket(HDHomeRunPacketType type) {
        BUFFER.clear();
        BUFFER.putShort(type.MASK);

        // This is the length of the packet.
        BUFFER.putShort((short) 0);
    }

    /**
     * Sets packet length and appends CRC.
     * <p/>
     * This also flips the buffer so we are ready to send.
     */
    public void endPacket() {
        int currentLen = BUFFER.position();
        int payloadLength = currentLen - 4;

        // Now we can set the length of the packet.
        BUFFER.putShort(2, (short) payloadLength);

        BUFFER.flip();
        int crc = calculateCRC(BUFFER);
        BUFFER.compact();

        BUFFER.order(ByteOrder.LITTLE_ENDIAN);
        try {
            BUFFER.putInt(crc);
        } finally {
            BUFFER.order(ByteOrder.BIG_ENDIAN);
        }

        BUFFER.flip();
    }


    /**
     * Calculate the CRC
     *
     * @param buffer This is the buffer to calculate the CRC of. Be sure to flip the buffer before
     *               submitting it.
     * @return This is the calculated CRC value.
     */
    public static int calculateCRC(ByteBuffer buffer) {
        CRC32 checksum = new CRC32();

        checksum.reset();

        if (buffer.hasArray()) {
            checksum.update(buffer.array(), buffer.arrayOffset(), buffer.remaining());
        } else {
            buffer.mark();

            while (buffer.hasRemaining()) {
                checksum.update(buffer.get());
            }

            buffer.reset();
        }

        int crc = (int) (checksum.getValue() & 0xffffffff);

        return crc;
    }

    public void putTagLengthValue(HDHomeRunPacketTag tag, int value) {
        BUFFER.put(tag.MASK);
        putVariableLength(4);
        BUFFER.putInt(value);
    }

    public void putTagLengthValue(HDHomeRunPacketTag tag, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        BUFFER.put(tag.MASK);
        putVariableLength(bytes.length + 1);
        BUFFER.put(bytes);

        // Add null termination byte for strings.
        BUFFER.put((byte) 0);
    }

    private void putVariableLength(int length) {
        if (length <= 127) {
            // We need to mask the byte because Java signs bytes.
            BUFFER.put((byte) (length & 0x7f));
        } else {
            BUFFER.put((byte) (length | 0x80));
            BUFFER.put((byte) (length >> 7));
        }
    }

    public static HDHomeRunPacketTag getTagByValue(short value) {
        for (HDHomeRunPacketTag tag : HDHomeRunPacketTag.values()) {
            if (tag.MASK == value) {
                return tag;
            }
        }

        // What is 0x27?
        //logger.warn("Unable to find the enum for tag {}.", value);
        return null;
    }

    public static HDHomeRunPacketType getTypeByValue(short value) {
        for (HDHomeRunPacketType type : HDHomeRunPacketType.values()) {
            if (type.MASK == value) {
                return type;
            }
        }

        //logger.warn("Unable to find the enum for packet type {}.", value);
        return null;
    }

    public HDHomeRunPacketType getPacketType() {
        short type = BUFFER.getShort();

        return getTypeByValue(type);
    }

    public short getPacketLength() {
        short length = BUFFER.getShort();

        return length;
    }

    public HDHomeRunPacketTag getTag() {
        byte type = BUFFER.get();

        return getTagByValue(type);
    }

    public int getVariableLength() {
        if (!BUFFER.hasRemaining()) {
            return 0;
        }

        int length = BUFFER.get() & 0xff;

        if ((length & 0x0080) > 0) {
            if (!BUFFER.hasRemaining()) {
                return 0;
            }

            length &= 0x007F;
            length |= (BUFFER.get() & 0xff) << 7;
        }

        return length;
    }

    public Integer getTLVInteger() {
        if (BUFFER.remaining() < 4) {
            return null;
        }

        return BUFFER.getInt();
    }

    public String getTLVString(int length) {
        if (BUFFER.remaining() < length) {
            return null;
        }

        byte stringBytes[] = new byte[length - 1];
        for (int i = 0; i < stringBytes.length; i++) {
            stringBytes[i] = BUFFER.get();
        }

        BUFFER.get();

        return new String(stringBytes, StandardCharsets.UTF_8);
    }

}
