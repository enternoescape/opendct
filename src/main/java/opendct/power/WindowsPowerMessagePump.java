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

package opendct.power;

import com.sun.jna.WString;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import opendct.config.ExitCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class WindowsPowerMessagePump implements Runnable, PowerMessagePump {
    private final Logger logger = LogManager.getLogger(WindowsPowerMessagePump.class);

    private Thread thread;
    private AtomicBoolean running = new AtomicBoolean(false);
    private WNDCLASSEX wndClass = null;
    private WindowProc wndProc = null;
    private HWND hWnd = null;

    // This prevents a listener from being added or removed while an event is being broadcasted.
    private final Object eventLock = new Object();
    private List<PowerEventListener> eventListeners = new ArrayList<PowerEventListener>();

    public WindowsPowerMessagePump() {
        thread = new Thread(this);
        thread.setName(this.getClass().getSimpleName());
    }

    public void addListener(PowerEventListener listener) {
        logger.entry(listener);

        synchronized (eventLock) {
            for (PowerEventListener eventListener : eventListeners) {
                if (eventListener == listener) {
                    // This will help weed out redundancy.
                    logger.warn("'{}' is already listening.", listener.getClass().getName());
                    return;
                }
            }

            logger.debug("'{}' is now listening.", listener.getClass().toString());
            eventListeners.add(listener);
        }

        logger.exit();
    }

    public void removeListener(PowerEventListener listener) {
        logger.entry(listener);

        synchronized (eventLock) {
            if (!eventListeners.remove(listener)) {
                // Doesn't matter that much, but I guess it could be significant given the right
                // situation.
                logger.trace("'{}' was not already listening.", listener.getClass().getName());
            } else {
                logger.debug("'{}' is no longer listening.", listener.getClass().getName());
            }
        }

        logger.exit();
    }

    public boolean startPump() {
        logger.entry();

        if (running.getAndSet(true)) {
            logger.warn("Already running.");
            return logger.exit(true); // We don't want this to cause the program to terminate.
        }

        try {
            thread.start();
        } catch (Exception e) {
            running.set(false);
            logger.fatal("Unhandled exception => {}", e);
            return logger.exit(false);
        }

        return logger.exit(true);
    }

    public void run() {
        try {
            if (!createWindow()) {
                logger.error("Unable to create window for message pump" +
                        "to receive broadcast messages.");

                ExitCode.PM_INIT.terminateJVM();
            }

            WinUser.MSG msg = new WinUser.MSG();

            int getMsgResult = -2;

            logger.info("Message pump started.");

            while ((getMsgResult = User32.INSTANCE.GetMessage(msg, hWnd, 0, 0)) > 0) {
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }

            logger.info("Message pump stopped.");

            if (getMsgResult == -1) {
                logger.fatal("GetMessage failed. GetLastError returned " +
                        Kernel32.INSTANCE.GetLastError());

                ExitCode.PM_GET_MESSAGE.terminateJVM();
            }

        } catch (Exception e) {
            logger.error("There was an unhandled exception while processing messages => {}", e);

            ExitCode.PM_EXCEPTION.terminateJVM();
        } finally {
            destroyWindow();
            running.set(false);
        }
    }

    private boolean createWindow() {
        wndProc = new WindowProc() {
            public LRESULT callback(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam) {
                if (msg == User32Ex.WM_POWERBROADCAST) {
                    switch (wParam.intValue()) {
                        case User32Ex.PBT_APMSUSPEND:
                            synchronized (eventLock) {
                                for (PowerEventListener eventListener : eventListeners) {
                                    eventListener.onSuspendEvent();
                                }
                            }

                            return new LRESULT(1);

                        case User32Ex.PBT_APMRESUMESUSPEND:
                            synchronized (eventLock) {
                                for (PowerEventListener eventListener : eventListeners) {
                                    eventListener.onResumeSuspendEvent();
                                }
                            }

                            return new LRESULT(1);

                        case User32Ex.PBT_APMRESUMEAUTOMATIC:
                            //TODO: [js] This should probably be removed completely.
                            /*synchronized (eventLock) {
                                for (PowerEventListener eventListener : eventListeners) {
                                    eventListener.onResumeAutomaticEvent();
                                }
                            }*/

                            return new LRESULT(1);

                        case User32Ex.PBT_APMRESUMECRITICAL:
                            synchronized (eventLock) {
                                for (PowerEventListener eventListener : eventListeners) {
                                    eventListener.onResumeCriticalEvent();
                                }
                            }

                            return new LRESULT(1);
                    }
                }

                return User32.INSTANCE.DefWindowProc(hWnd, msg, wParam, lParam);
            }
        };

        wndClass = new WNDCLASSEX();
        wndClass.lpszClassName = new WString("OpenDCTPowerBroadcastClass");
        wndClass.lpfnWndProc = wndProc;

        if (User32.INSTANCE.RegisterClassEx(wndClass).intValue() != 0) {
            hWnd = User32.INSTANCE.CreateWindowEx(0, wndClass.lpszClassName, null, 0, 0, 0, 0, 0, null, null, null, null);
            return logger.exit(true);
        }

        return logger.exit(false);
    }

    private void destroyWindow() {
        if (hWnd != null) {
            User32.INSTANCE.DestroyWindow(hWnd);
            hWnd = null;
        }

        if (wndClass != null) {
            User32.INSTANCE.UnregisterClass(wndClass.lpszClassName, null);
            wndClass = null;
        }
    }

    public void stopPump() {
        if (hWnd == null) {
            return;
        }

        logger.info("Posting WM_QUIT message and waiting for message pump thread to exit.");
        User32.INSTANCE.PostMessage(hWnd, User32.WM_QUIT, null, null);

        try {
            if (!Thread.currentThread().equals(thread)) {
                thread.join();
            }
        } catch (InterruptedException ie) {
            thread.interrupt();
        }
    }

    public void testSuspend() {
        synchronized (eventLock) {
            for (PowerEventListener eventListener : eventListeners) {
                eventListener.onSuspendEvent();
            }
        }
    }

    public void testResume() {
        synchronized (eventLock) {
            for (PowerEventListener eventListener : eventListeners) {
                eventListener.onResumeSuspendEvent();
            }
        }
    }
}

