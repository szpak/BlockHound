package reactor.core.scheduler;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class MyThreadFactory implements ThreadFactory {

    private AtomicLong threadCounter = new AtomicLong(0);

    @Override
    public Thread newThread(Runnable r) {
        final NonBlockingThread thread = new NonBlockingThread(r, "xxx-" + threadCounter.incrementAndGet());
        thread.setUncaughtExceptionHandler((t, e) -> e.printStackTrace());
        return thread;
    }

    static final class NonBlockingThread extends Thread implements NonBlocking {
        NonBlockingThread(Runnable target, String name) {
            super(target, name);
        }
    }

}
