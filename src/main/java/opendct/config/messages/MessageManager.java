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

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import opendct.config.ConfigBag;
import opendct.util.Util;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MessageManager {
    private static final Logger logger = LogManager.getLogger(MessageManager.class);

    private static final ReentrantReadWriteLock messageLock;

    private static final ConfigBag messageConfig;
    private static final int MESSAGE_LIMIT = 100;

    private static boolean dismissed;
    private static Level currentLevel;
    private static final Map<Integer, MessageContainer> messages;

    static {
        messageLock = new ReentrantReadWriteLock();
        messageConfig = new ConfigBag("messages", false);
        messages = new Int2ObjectOpenHashMap<>();
        dismissed = true;
        currentLevel = Level.INFO;
    }

    public static void saveMessages() {
        messageLock.readLock().lock();

        try {
            messageConfig.removeAllByRootKey("messages.saved.");

            messageConfig.setBoolean("messages.dismissed", dismissed);

            int i = 0;
            for (Map.Entry<Integer, MessageContainer> messageKvp : messages.entrySet()) {
                MessageContainer messageContainer = messageKvp.getValue();

                messageConfig.setString("messages.saved." + i + ".level", messageContainer.getLevel().name());
                messageConfig.setString("messages.saved." + i + ".title", messageContainer.getTitle());
                messageConfig.setString("messages.saved." + i + ".message", messageContainer.getMessage());
                messageConfig.setString("messages.saved." + i + ".sender", messageContainer.getSender());
                messageConfig.setLongArray("messages.saved." + i + ".times", messageContainer.getTime());
            }
        } finally {
            messageLock.readLock().unlock();
        }
    }

    public static void loadMessages() {
        messageLock.writeLock().lock();

        try {
            messages.clear();
            dismissed = messageConfig.getBoolean("messages.dismissed", dismissed);

            Map<String, String> loadMessages = messageConfig.getAllByRootKey("messages.saved.");

            int i = 0;
            while(true) {
                String loadLevel = loadMessages.get("messages.saved." + i + ".level");
                String loadTitle = loadMessages.get("messages.saved." + i + ".title");
                String loadMessage = loadMessages.get("messages.saved." + i + ".message");
                String loadSender = loadMessages.get("messages.saved." + i + ".sender");
                String loadTimes = loadMessages.get("messages.saved." + i + ".times");

                if (loadLevel == null ||
                        loadTitle == null ||
                        loadMessage == null ||
                        loadSender == null ||
                        loadTimes == null) {

                    break;
                }

                String loadTimesArray[] = loadTimes.split("\\s*,\\s*");

                if (loadTimesArray.length == 1) {
                    if (Util.isNullOrEmpty(loadTimesArray[0])) {
                        loadTimesArray = new String[0];
                    }
                }

                long loadTimesInt[] = new long[loadTimesArray.length];

                for (int j = 0; j < loadTimesInt.length; j++) {
                    try {
                        loadTimesInt[j] = Long.parseLong(loadTimesArray[j]);
                    } catch (NumberFormatException e) {
                        logger.warn("Unable to parse long from '{}', using default 0 => ", loadTimesArray[j], e);
                        loadTimesInt[j] = 0;
                    }
                }

                MessageContainer messageContainer = new MessageContainerImpl(loadSender, Level.toLevel(loadLevel, Level.INFO), loadTitle, loadMessage, loadTimesInt);

                i += 1;
            }
        } finally {
            messageLock.writeLock().unlock();
        }
    }

    public static void setDismissed() {
        messageLock.writeLock().lock();

        try {
            dismissed = true;
            messageConfig.setBoolean("messages.dismissed", true);
        } finally {
            messageLock.writeLock().unlock();
        }
    }

    public static boolean isDismissed() {
        boolean returnValue;

        messageLock.readLock().lock();

        try {
            returnValue = dismissed;
        } finally {
            messageLock.readLock().unlock();
        }

        return returnValue;
    }

    public static void clearMessages() {
        messageLock.writeLock().lock();

        try {
            currentLevel = Level.INFO;
            messages.clear();
        } finally {
            messageLock.writeLock().unlock();
        }
    }

    public static Level getCurrentLevel() {
        Level returnValue;

        messageLock.readLock().lock();

        try {
            returnValue = currentLevel;
        } finally {
            messageLock.readLock().unlock();
        }

        return returnValue;
    }

    /**
     * Add a new message to the queue.
     * <p/>
     * Note that anything added here will appear on the web interface, so think very carefully about
     * what you want the user to see.
     *
     * @param sender The object sending the message.
     * @param level The severity of the message.
     * @param title The title for this message.
     * @param message The actual message.
     */
    public static void message(Object sender, Level level, String title, String message) {
        MessageContainer messageContainer = new MessageContainerImpl(sender.getClass().getCanonicalName(), level, title, message);

        messageLock.writeLock().lock();

        try {
            MessageContainer foundContainer = messages.get(messageContainer.hashCode());

            if (foundContainer == null && messages.size() < MESSAGE_LIMIT) {
                messages.put(messageContainer.hashCode(), messageContainer);
            } else if (foundContainer != null) {
                foundContainer.incrementRepeat();
                messageContainer = foundContainer;

            }
        } finally {
            messageLock.writeLock().unlock();
        }

        messageContainer.logMessage(null);

    }

    /**
     * Add a new info severity message to the queue.
     * <p/>
     * Note that anything added here will appear on the web interface, so think very carefully about
     * what you want the user to see.
     *
     * @param sender The object sending the message.
     * @param title The title for this message.
     * @param message The actual message.
     */
    public static void info(Object sender, String title, String message) {
        message(sender, Level.INFO, title, message);
    }

    /**
     * Add a new warning severity message to the queue.
     * <p/>
     * Note that anything added here will appear on the web interface, so think very carefully about
     * what you want the user to see.
     *
     * @param sender The object sending the message.
     * @param title The title for this message.
     * @param message The actual message.
     */
    public static void warn(Object sender, String title, String message) {
        message(sender, Level.WARN, title, message);
    }

    /**
     * Add a new error severity message to the queue.
     * <p/>
     * Note that anything added here will appear on the web interface, so think very carefully about
     * what you want the user to see.
     *
     * @param sender The object sending the message.
     * @param title The title for this message.
     * @param message The actual message.
     */
    public static void error(Object sender, String title, String message) {
        message(sender, Level.ERROR, title, message);
    }

    /**
     * Add a new info severity message to the queue.
     * <p/>
     * Note that anything added here will appear on the web interface, so think very carefully about
     * what you want the user to see.
     *
     * @param sender The object sending the message.
     * @param title The title for this message.
     * @param message The actual message.
     */
    public static void info(Object sender, MessageTitle title, String message) {
        message(sender, Level.INFO, title.TITLE, message);
    }

    /**
     * Add a new warning severity message to the queue.
     * <p/>
     * Note that anything added here will appear on the web interface, so think very carefully about
     * what you want the user to see.
     *
     * @param sender The object sending the message.
     * @param title The title for this message.
     * @param message The actual message.
     */
    public static void warn(Object sender, MessageTitle title, String message) {
        message(sender, Level.WARN, title.TITLE, message);
    }

    /**
     * Add a new error severity message to the queue.
     * <p/>
     * Note that anything added here will appear on the web interface, so think very carefully about
     * what you want the user to see.
     *
     * @param sender The object sending the message.
     * @param title The title for this message.
     * @param message The actual message.
     */
    public static void error(Object sender, MessageTitle title, String message) {
        message(sender, Level.ERROR, title.TITLE, message);
    }
}
