package reactor.core.scheduler;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class ReactorThreadFactoryDelegatorUtil {

    public static ThreadFactory createNonBlockingThreadFactory() {
        return new ReactorThreadFactory("non-blocking", new AtomicLong(), true, true, (t, e) -> e.printStackTrace());
    }

    public static ThreadFactory createBlockingFriendlyThreadFactory() {
        return new ReactorThreadFactory("blocking-friendly", new AtomicLong(), true, false, (t, e) -> {});
    }
}
