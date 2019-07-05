package reactor.blockhound;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static reactor.core.scheduler.Schedulers.single;

@SuppressWarnings("WeakerAccess")
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(1)
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class BlockHoundBlockingExceptionBenchmark {

    String source = "foo";
    Mono<String> just ;//= Mono.just("foo");

    Mono<String> blockingMinimallyMono ;/*= Mono.fromCallable(() -> {
        Thread.yield();
        return "";
    });           */

    @Setup(Level.Iteration)
    public void createMonos() {
        just = Mono.just(source);
        blockingMinimallyMono = Mono.fromCallable(() -> {
                Thread.yield();
                return source;
            });
    }

    @State(Scope.Benchmark)
    public static class BlockHoundInstalledState {
        @Setup
        public void prepare() {
            System.out.println("Debug: Installing Block Hound");
            BlockHound.install();
        }
    }

//    @Benchmark
    public void baselineBlockingMonoSimpleConsumer(Blackhole blackhole) {
        callBlockingMonoSubscribedOnSingleAndHandleException(blackhole, () -> {
            blockingMinimallyMono.subscribe(blackhole::consume);
            return null;
        });
    }

//    @Benchmark
    public void measureBlockingMonoSimpleConsumer(Blackhole blackhole, BlockHoundInstalledState state) {
        callBlockingMonoSubscribedOnSingleAndHandleException(blackhole, () -> {
            blockingMinimallyMono.subscribe(blackhole::consume);
            return null;
        });
    }

    //TODO: Warto custom handler, ktory tworzy wyjatek testować? - aby ludzie wiedzieli, czy warto produkcyjnie to robić
    // - moze tez zwykly tez do tego dodac?

//    @Deprecated  //redundant?
//    @Benchmark
    public String baselineNonBlockingMonoSubscribedOnSingleWithFakeExceptionThrowing0(Blackhole blackhole) {
        return callBlockingMonoSubscribedOnSingleAndHandleException(blackhole,
                () -> blockingMinimallyMono.subscribeOn(single()).block(Duration.ofSeconds(1)));
    }

//    @Benchmark
    @SuppressWarnings("squid:S00112")
    public String baselineNonBlockingMonoSubscribedOnSingleWithFakeExceptionThrowing(Blackhole blackhole) {
        return callBlockingMonoSubscribedOnSingleAndHandleException(blackhole, () -> {
            blockingMinimallyMono.subscribeOn(single()).block(Duration.ofSeconds(1));
            //won't it be optimized?
            throw new RuntimeException("java.lang.Error: Blocking call! Fake blocking call to have some baseline for exception throwing and handling cost");
        });
    }

//    @Benchmark
    public String measureBlockingMonoSubscribedOnSingle(BlockHoundInstalledState state, Blackhole blackhole) {
        return callBlockingMonoSubscribedOnSingleAndHandleException(blackhole,
                () -> blockingMinimallyMono.subscribeOn(single()).block(Duration.ofSeconds(1)));
    }

    private String callBlockingMonoSubscribedOnSingleAndHandleException(Blackhole blackhole, Supplier<String> callable) {
        try {
            return callable.get();
        } catch (RuntimeException e) {
            if (e.getMessage().startsWith("java.lang.Error: Blocking call!")) { //TODO: Exceptions.ReactiveException is not public...
                blackhole.consume(e);
                return e.getMessage();
            } else {
                throw e;
            }
        }
    }

}
