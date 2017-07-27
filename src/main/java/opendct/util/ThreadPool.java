/*
 * Copyright 2015-2016 The OpenDCT Authors. All Rights Reserved
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opendct.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;

public class ThreadPool extends ThreadPoolExecutor {
    private final static Logger logger = LogManager.getLogger(ThreadPoolExecutor.class);
    private final static ExecutorService executorService;

    static {
        executorService = new ThreadPool();
    }

    public static Future submit(final Runnable runnable, final int priority, final String name, final String postPend) {
        return executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread thread = Thread.currentThread();
                    thread.setName(name + "-" + thread.getId() + ":" + postPend);
                    thread.setPriority(priority);
                    runnable.run();
                } catch (Throwable e) {
                    logger.error("Thread threw unhandled exception => ", e);
                }
            }
        });
    }

    public ThreadPool() {
        super(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
    }
}
