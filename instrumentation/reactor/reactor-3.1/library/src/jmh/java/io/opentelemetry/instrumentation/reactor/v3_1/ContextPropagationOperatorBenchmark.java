/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.reactor.v3_1;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Fork(value = 10, warmups = 10)
@Warmup(iterations = 0, time = 1)
@Measurement(iterations = 1, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.Throughput)
@State(Scope.Thread)
public class ContextPropagationOperatorBenchmark {

    private static final Object TRACE_CONTEXT_KEY =
        new Object() {
            @Override
            public String toString() {
                return "otel-trace-context";
            }
        };

    private static final ContextKey<String> KEY = ContextKey.named("ContextPropagationOperatorBenchmark.value");

    private static final boolean ENABLE_CONTEXT_PROPAGATION = true;

    private static final boolean VALIDATE_CONTEXT_PROPAGATION = true;

    private static final ContextPropagationOperator singleton = new ContextPropagationOperatiorBuilder

    private static final AtomicInteger counter = new AtomicInteger(0);

    private final static ContextPropagationOperator singleton = createAndInitializeContextPropagationOperator;

    @Benchmark
    public void simulatedFastIOOperation() {

        Context initialTraceCtx = Context.current().with(KEY, counter.incrementAndGet());

        Mono<Integer> mono = Mono
            .fromCallable(() -> {

                // TraceContext is required here
                // In subsequent steps of the reactor pipeline we can live in our
                // SDK withut TraceContext being propagated
                // What I am looking for is teh Reactor pendant of .Net's Task.ConfigureAwait(false)
                // to suppress synchroniation context propagation to optimize perf
                validateTraceContext(initialTraceCtx);

                return 1
            })
            .publishOn(reactor.core.scheduler.Schedulers.parallel())
            .delayElement(Duration.ofMillis(1L))
            .map(input -> {
                if (VALIDATE_CONTEXT_PROPAGATION) {
                    validateTraceContext(traceContext);
                }
                return input + 1
            })
            .publishOn(reactor.core.scheduler.Schedulers.elastic())
            .map(input -> {
                if (VALIDATE_CONTEXT_PROPAGATION) {
                    validateTraceContext(traceContext);
                }
                return input + 1
            })
            .contextWrite(reactorCtx -> reactorCtx.put(TRACE_CONTEXT_KEY, traceCtx);

        if (3 != mono.block())) {
            throw new IllegalStateException("Unexpected result");
        }
    }

    private static validateTraceContext(Context expectedTraceCtx) {
        if (expectedTraceCtx != Context.current() {
            throw new IllegalStateException("Unexpected trace context.");
        }
    }

    private static ContextPropagationOperator createAndInitializeContextPropagationOperator() {
        ContextPropagationOperator result =
            new ContextPropagationOperatorBuilder().setCaptureExperimentalSpanAttributes(false).build();

        if (ENABLE_CONTEXT_PROPAGATION) {
            result.registerOnEachOperator();
        }

        return result;
    }

    public static void main(String[] args) {
        Options opt = new OptionsBuilder()
            .include(ContextPropagationOperatorBenchmark.class.getSimpleName())
            .build();

        new Runner(opt).run();
    }
}