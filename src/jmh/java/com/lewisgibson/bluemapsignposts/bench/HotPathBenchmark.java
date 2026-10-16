package com.lewisgibson.bluemapsignposts.bench;

import com.lewisgibson.bluemapsignposts.core.sign.ParseResult;
import com.lewisgibson.bluemapsignposts.core.sign.SignpostParser;
import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostRegistry;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * Microbenchmarks for the paths that run at gameplay frequency. Every chunk load and every block
 * break server-wide asks the registry a question, and every sign edit runs the parser, so these are
 * the only methods whose cost is paid often. Results are in nanoseconds per operation.
 *
 * <p>Run with {@code ./gradlew jmh}. The registry is populated with a thousand signposts spread
 * over a thousand chunks so the lookups are measured against a realistic index, not an empty one.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class HotPathBenchmark {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final SignpostParser parser = new SignpostParser("[map]");
    private final SignpostRegistry registry = new SignpostRegistry();
    private final List<String> ordinarySign = List.of("Welcome", "to", "spawn", "");
    private final List<String> signpostSign = List.of("[map]", "Iron", "farm", "");

    public HotPathBenchmark() {
        for (int index = 0; index < 1000; index++) {
            registry.put(
                    new Signpost(
                            new SignpostKey("world", index * 16, 64, index * 16),
                            "Signpost " + index,
                            OWNER,
                            "Owner",
                            SignpostSource.SIGN,
                            0L));
        }
    }

    /** The by-far most common case: a block event in a world with no signposts at all. */
    @Benchmark
    public boolean emptyWorldCheck() {
        return registry.isEmptyIn("world_the_end");
    }

    /** A chunk load in a world with signposts, for a chunk holding none. */
    @Benchmark
    public List<Signpost> chunkLookupMiss() {
        return registry.inChunk("world", -5, -5);
    }

    /** A chunk load for a chunk holding one signpost. */
    @Benchmark
    public List<Signpost> chunkLookupHit() {
        return registry.inChunk("world", 7, 7);
    }

    /** A block break at a block with no signpost (the key allocation plus one hash lookup). */
    @Benchmark
    public boolean blockLookupMiss() {
        return registry.get(new SignpostKey("world", 3, 70, 3)).isPresent();
    }

    /** An ordinary sign edit: the parser rejects it on the first line. */
    @Benchmark
    public ParseResult parseOrdinarySign() {
        return parser.parse(ordinarySign);
    }

    /** A signpost sign edit: the parser builds the label. */
    @Benchmark
    public ParseResult parseSignpost() {
        return parser.parse(signpostSign);
    }
}
