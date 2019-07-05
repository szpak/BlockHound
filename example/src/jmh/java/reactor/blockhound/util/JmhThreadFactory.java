package reactor.blockhound.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class JmhThreadFactory implements ThreadFactory {

    private final boolean isBlockingAllowed;
    private final String namePrefix;
    private final AtomicLong threadCounter;

    private JmhThreadFactory(boolean isBlockingAllowed) {
        this.isBlockingAllowed = isBlockingAllowed;
        this.namePrefix = isBlockingAllowed ? "blocking-" : "nonBlocking-";
        this.threadCounter  = new AtomicLong(0);
    }

    public static ThreadFactory blockingAllowedThreadFactory() {
        return new JmhThreadFactory(true);
    }

    public static ThreadFactory blockingNotAllowedThreadFactory() {
        return new JmhThreadFactory(false);
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = isBlockingAllowed ?
                new Thread(r, namePrefix + threadCounter.incrementAndGet()) :
                new NonBlockingThread(r, namePrefix + threadCounter.incrementAndGet());
        thread.setUncaughtExceptionHandler((t, e) -> e.printStackTrace());  //TODO
        return thread;
    }

    static class NonBlockingThread extends Thread implements JmhNonBlocking {
        NonBlockingThread(Runnable target, String name) {
            super(target, name);
        }
    }

}
