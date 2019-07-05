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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static reactor.core.scheduler.ReactorThreadFactoryDelegatorUtil.createBlockingFriendlyThreadFactory;

@SuppressWarnings("WeakerAccess")
//quick settings for IDE - overridden in Gradle configuration
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@Fork(3)
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class BlockHoundBlockingAllowedThreadBenchmark {

    String fuzzier = "foo";
    ExecutorService blockingAllowedSingleThreadExecutor;

    Callable<String> slightlyBlockingCallable = () -> {
        Thread.yield();
        return fuzzier;
    };

    @Setup(Level.Iteration)
    public void createObjectWithNonBlockingExecution() {
        blockingAllowedSingleThreadExecutor = Executors.newSingleThreadExecutor(createBlockingFriendlyThreadFactory());
    }

    @TearDown(Level.Iteration)
    public void cleanupExecutors() {
        blockingAllowedSingleThreadExecutor.shutdown();
    }

    @State(Scope.Benchmark)
    public static class BlockHoundInstalledState {
        @Setup
        public void prepare() {
            System.out.println("Debug: Installing Block Hound");
            BlockHound.install();
        }
    }

    @Benchmark  //not fully reliable as executor call is internally blocking: https://github.com/reactor/BlockHound/issues/38
    public String baselineNonBlockingCall() throws ExecutionException, InterruptedException {
        return blockingAllowedSingleThreadExecutor.submit(() -> fuzzier).get();
    }

    @Benchmark
    public String measureNonBlockingCall(BlockHoundInstalledState state) throws ExecutionException, InterruptedException {
        return blockingAllowedSingleThreadExecutor.submit(() -> fuzzier).get();
    }

    @Benchmark
    public String baselineNonBlockingSimpleCall() {
        return fuzzier;
    }

    @Benchmark
    public String measureNonBlockingSimpleCall(BlockHoundInstalledState state) {
        return fuzzier;
    }

    @Benchmark
    public String baselineBlockingCall() throws ExecutionException, InterruptedException {
        return blockingAllowedSingleThreadExecutor.submit(slightlyBlockingCallable).get();
    }

    @Benchmark
    public String measureBlockingCall(BlockHoundInstalledState state) throws ExecutionException, InterruptedException {
        return blockingAllowedSingleThreadExecutor.submit(slightlyBlockingCallable).get();
    }

}
