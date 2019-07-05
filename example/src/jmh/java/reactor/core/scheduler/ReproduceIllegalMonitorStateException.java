package reactor.core.scheduler;

import reactor.blockhound.BlockHound;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class ReproduceIllegalMonitorStateException {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ThreadFactory blockingUnfriendlyThreadFactory = new ReactorThreadFactory("blocking-unfriendly", new AtomicLong(), false, true,
                (t, e) -> e.printStackTrace());

        ThreadFactory myThreadFactory = new MyThreadFactory();

//        ExecutorService executor = Executors.newSingleThreadExecutor(blockingUnfriendlyThreadFactory);
        ExecutorService executor = Executors.newSingleThreadExecutor(myThreadFactory);
        try {
            BlockHound.install();
            System.out.println(executor.submit(() -> "result").get());
        } finally {
            executor.shutdown(); //fails with non daemon thread with "java.lang.IllegalMonitorStateException" on "ReentrantLock$Sync.tryRelease()"
        }

//        final BlockHoundNonBlocking2Benchmark b = new BlockHoundNonBlocking2Benchmark();
//        b.createObjectWithNonBlockingExecution();
//        new BlockHoundNonBlocking2Benchmark.BlockHoundInstalledState().prepare();
//        String result = b.nonBlockingSingleThreadExecutor.submit(() -> b.fuzzier).get();
//        System.out.println(result);
//        b.nonBlockingSingleThreadExecutor.shutdown();
//        /* Sypie się z BH na shutdown z wątkami "niedemonicznymi" (zgłosić):
//        java.lang.IllegalMonitorStateException
//        	at java.base/java.util.concurrent.locks.ReentrantLock$Sync.tryRelease(ReentrantLock.java:149)
//        	at java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.release(AbstractQueuedSynchronizer.java:1302)
//        	at java.base/java.util.concurrent.locks.ReentrantLock.unlock(ReentrantLock.java:439)
//        	at java.base/java.util.concurrent.LinkedBlockingQueue.take(LinkedBlockingQueue.java:440)
//        	at java.base/java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1054)
//        	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1114)
//        	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
//        	at java.base/java.lang.Thread.run(Thread.java:834)
//         */
    }
}
