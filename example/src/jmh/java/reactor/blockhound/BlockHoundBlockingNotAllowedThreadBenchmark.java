package reactor.blockhound;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import reactor.blockhound.integration.BlockHoundIntegration;

import javax.swing.text.html.Option;
import java.util.OptionalInt;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static reactor.blockhound.BlockHound.builder;
import static reactor.core.scheduler.ReactorThreadFactoryDelegatorUtil.createNonBlockingThreadFactory;

@SuppressWarnings({"WeakerAccess"})
//quick settings for IDE - overridden in Gradle configuration
@Warmup(iterations = 1)
@Measurement(iterations = 1)
@Fork(1)
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class BlockHoundBlockingNotAllowedThreadBenchmark {

    String fuzzier = "foo";
    int intFuzzier = 42;
    ExecutorService blockingNotAllowedSingleThreadExecutor;

    Callable<String> slightlyBlockingCallable = () -> {
        DummyBlocking.dummyBlocking();
        return fuzzier;
    };

    //TODO: Report that doesn't work with method reference :)
    @SuppressWarnings({"OptionalGetWithoutIsPresent", "Convert2MethodRef"})
    Callable<Integer> artificialBlockingCallable = () -> DummyBlocking.dummyBlockingInt(intFuzzier).getAsInt();

    public static class DummyBlocking {
        public static void dummyBlocking() {
            Thread.yield();
        }
        public static OptionalInt dummyBlockingInt(int value) {
            return OptionalInt.of(value);    //our artificial blocking method call
        }
    }

    @Setup(Level.Iteration)
    public void createObjectWithNonBlockingExecution() {
        blockingNotAllowedSingleThreadExecutor = Executors.newSingleThreadExecutor(createNonBlockingThreadFactory());
    }

    @TearDown(Level.Iteration)
    public void cleanupExecutors() {
        blockingNotAllowedSingleThreadExecutor.shutdown();
    }

    public abstract static class BlockHoundInstalledState {
        //It's just a protection against badly written case which does nothing. We ignore issues with access from multiple threads, as it's enough
        //to have it increased by one by any of the threads to assume the benchmark logic works.
        int detectedCallsCounter;

        @Setup
        public void prepare() {
            System.out.println("Debug: Installing Block Hound");
            BlockHound.Builder builder = builder();
            configureBuilder(builder);
            builder.install();
        }

        protected void configureBuilder(BlockHound.Builder builder) {
            ServiceLoader<BlockHoundIntegration> serviceLoader = ServiceLoader.load(BlockHoundIntegration.class);
            serviceLoader.stream().map(ServiceLoader.Provider::get).sorted().forEach(builder::with);

//            builder.markAsBlocking("reactor.blockhound.BlockHoundBlocking2Benchmark$DummyBlocking", "dummyBlocking", "()V");  //TODO: Broken: https://github.com/reactor/BlockHound/issues/39
            builder.markAsBlocking(OptionalInt.class, "of", "(I)Ljava/util/OptionalInt;");

//            builder.allowBlockingCallsInside("java.util.concurrent.locks.ReentrantLock$Sync", "tryRelease");
//            builder.allowBlockingCallsInside("java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject", "await");
//            builder.allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor$Worker", "run");

            //Due to: https://github.com/reactor/BlockHound/issues/38
            builder.allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "getTask");

            builder.blockingMethodCallback(bm -> {
                if (detectedCallsCounter == 0) {
                    System.out.println("Blocking method: " + bm.toString());
//                    System.err.println(Thread.currentThread().toString());
//                    new Error(bm.toString()).printStackTrace();
                }
                detectedCallsCounter++;
            });
        }

        @Setup(Level.Iteration)
        public void resetDetections() {
            detectedCallsCounter = 0;
        }
    }

    @State(Scope.Benchmark)
    public static class BlockHoundWithAllowedYieldInstalled extends BlockHoundInstalledState {
        @Override
        protected void configureBuilder(BlockHound.Builder builder) {
            super.configureBuilder(builder);
            builder.allowBlockingCallsInside("reactor.blockhound.BlockHoundBlockingNotAllowedThreadBenchmark$DummyBlocking", "dummyBlocking");
            builder.allowBlockingCallsInside("reactor.blockhound.BlockHoundBlockingNotAllowedThreadBenchmark$DummyBlocking", "dummyBlockingInt");
        }

        @TearDown(Level.Iteration)
        public void assertDetections() {
            assertThat(detectedCallsCounter).describedAs("Unexpected blocking calls detected").isZero();
        }
    }

    @State(Scope.Benchmark)
    public static class BlockHoundWithNotAllowedYieldInstalled extends BlockHoundInstalledState {
        @Override
        protected void configureBuilder(BlockHound.Builder builder) {
            super.configureBuilder(builder);
            builder.disallowBlockingCallsInside("reactor.blockhound.BlockHoundBlockingNotAllowedThreadBenchmark$DummyBlocking", "dummyBlocking");
            builder.disallowBlockingCallsInside("reactor.blockhound.BlockHoundBlockingNotAllowedThreadBenchmark$DummyBlocking", "dummyBlockingInt");
            //TODO: class.getCanonicalName() doesn't work due to $
        }

        @TearDown(Level.Iteration)
        public void assertDetections() {
            assertThat(detectedCallsCounter).describedAs("No blocking calls detected").isGreaterThan(0);
        }
    }

    //TODO: baselineBlockingCallInNonBlockingThread
//    @Benchmark
    public String baselineBlockingCall() throws ExecutionException, InterruptedException {
//        return blockingNotAllowedSingleThreadExecutor.submit(slightlyBlockingCallable).get();
        return blockingNotAllowedSingleThreadExecutor.submit(artificialBlockingCallable).get() + "";
    }

//    @Benchmark    //TODO: Problematic in implementation as ThreadPoolExecutor itself contains blocking code - https://github.com/reactor/BlockHound/issues/38
//    public String measureNoBlockingCall(BlockHoundWithAllowedYieldInstalled state) throws ExecutionException, InterruptedException {
//        return blockingNotAllowedSingleThreadExecutor.submit(nonBlockingCallable).get() + "";
//    }

//    @Benchmark
    public String measureAllowedBlockingCall(BlockHoundWithAllowedYieldInstalled state) throws ExecutionException, InterruptedException {
//        return blockingNotAllowedSingleThreadExecutor.submit(slightlyBlockingCallable).get();
        return blockingNotAllowedSingleThreadExecutor.submit(artificialBlockingCallable).get() + "";
    }

    @Benchmark
    public String measureDisallowedBlockingCall(BlockHoundWithNotAllowedYieldInstalled state) throws ExecutionException, InterruptedException {
//        return blockingNotAllowedSingleThreadExecutor.submit(slightlyBlockingCallable).get();
        return blockingNotAllowedSingleThreadExecutor.submit(artificialBlockingCallable).get() + "";
    }
}
