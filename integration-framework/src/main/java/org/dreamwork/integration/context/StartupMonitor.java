package org.dreamwork.integration.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Created by seth.yang on 2020/4/22
 */
class StartupMonitor {
    private int timeout;
    private ExecutorService executor;
    private boolean error;

    private final Object LOCKER = new byte[0];
    private final Logger logger = LoggerFactory.getLogger (StartupMonitor.class);

    StartupMonitor (ExecutorService executor, int timeout) {
        this.timeout  = timeout;
        this.executor = executor;
    }

    boolean timing (ITask task) {
        long timestamp = System.currentTimeMillis ();
        Future<?> future = executor.submit (() -> {
            if (logger.isTraceEnabled ()) {
                logger.trace ("trying to launch task ...");
            }
            try {
                task.start ();
            } catch (Exception ex) {
                logger.warn (ex.getMessage (), ex);
                error = true;
            } finally {
                synchronized (LOCKER) {
                    LOCKER.notifyAll ();
                }
            }
        });
        synchronized (LOCKER) {
            try {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("wait up to {} ms for executing task", timeout + 100);
                }
                LOCKER.wait (timeout + 100);    // 多等100ms
            } catch (InterruptedException ex) {
                if (logger.isTraceEnabled ()) {
                    logger.warn (ex.getMessage (), ex);
                }
            }
        }
        long delta = System.currentTimeMillis () - timestamp;
        if (logger.isTraceEnabled ()) {
            logger.trace ("task execution take {} ms.", delta);
        }
        if (delta > timeout) {
            // 执行超时，强制停止
            future.cancel (true);
            try {
                task.stop ();
            } catch (Exception ex) {
                logger.warn (ex.getMessage (), ex);
            }
            return false;
        }
        return !error;
    }

    interface ITask {
        void start ();
        void stop  ();
    }
}
