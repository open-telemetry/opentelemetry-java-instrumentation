/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.twitter.util.Await;
import com.twitter.util.Duration;
import com.twitter.util.Future;
import com.twitter.util.FuturePool;
import com.twitter.util.FuturePool$;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import scala.Function0;
import scala.Function1;
import scala.runtime.AbstractFunction0;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;

class TwitterUtilCoreInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final Duration AWAIT = Duration.fromSeconds(10);

  @Test
  void constFutureRespondPropagatesContext() {
    testing.runWithSpan(
        "parent",
        () -> {
          Future.value("v")
              .respond(
                  fn1(
                      t -> {
                        testing.runWithSpan("respond-child", () -> {});
                        return BoxedUnit.UNIT;
                      }));
        });
    assertParentAndChild("parent", "respond-child");
  }

  @Test
  void constFutureTransformPropagatesContext() throws Exception {
    Future<String> result =
        testing.runWithSpan(
            "parent",
            () ->
                Future.value("v")
                    .transform(
                        fn1(
                            t -> {
                              testing.runWithSpan("transform-child", () -> {});
                              return Future.value("transformed");
                            })));
    assertThat(Await.result(result, AWAIT)).isEqualTo("transformed");
    assertParentAndChild("parent", "transform-child");
  }

  @Test
  void constFutureExceptionRespondPropagatesContext() {
    testing.runWithSpan(
        "parent",
        () -> {
          Future.exception(new RuntimeException("boom"))
              .respond(
                  fn1(
                      t -> {
                        testing.runWithSpan("respond-child", () -> {});
                        return BoxedUnit.UNIT;
                      }));
        });
    assertParentAndChild("parent", "respond-child");
  }

  @Test
  void futurePoolSingleApplyPropagatesContext() throws Exception {
    ExecutorService exec = Executors.newSingleThreadExecutor(named("single-pool"));
    cleanup.deferCleanup(() -> shutdown(exec));
    FuturePool pool = FuturePool$.MODULE$.apply(exec);

    Future<Integer> f =
        testing.runWithSpan(
            "parent",
            () ->
                pool.apply(
                    fn0(
                        () -> {
                          testing.runWithSpan("pool-child", () -> {});
                          return 1;
                        })));

    assertThat((int) Await.result(f, AWAIT)).isEqualTo(1);
    assertParentAndChild("parent", "pool-child");
  }

  @Test
  void futurePoolMultiLayerPropagatesContextAcrossPools() throws Exception {
    ExecutorService exec1 = Executors.newSingleThreadExecutor(named("pool-1"));
    ExecutorService exec2 = Executors.newSingleThreadExecutor(named("pool-2"));
    ExecutorService exec3 = Executors.newFixedThreadPool(2, named("pool-3"));
    cleanup.deferCleanup(() -> shutdown(exec1));
    cleanup.deferCleanup(() -> shutdown(exec2));
    cleanup.deferCleanup(() -> shutdown(exec3));

    FuturePool pool1 = FuturePool$.MODULE$.apply(exec1);
    FuturePool pool2 = FuturePool$.MODULE$.apply(exec2);
    FuturePool pool3 = FuturePool$.MODULE$.apply(exec3);

    Set<String> threadsSeen = ConcurrentHashMap.newKeySet();
    String callerThread = Thread.currentThread().getName();

    Future<String> chain =
        testing.runWithSpan(
            "parent",
            () ->
                pool1
                    .apply(
                        fn0(
                            () -> {
                              threadsSeen.add(Thread.currentThread().getName());
                              testing.runWithSpan("stage-1", () -> {});
                              return "a";
                            }))
                    // hop to pool2: the flatMap continuation runs on exec1 (completion thread)
                    // via the Promise$Transformer advice, and pool2.apply() re-captures context
                    // to dispatch the Function0 onto exec2
                    .flatMap(
                        fn1(
                            a ->
                                pool2.apply(
                                    fn0(
                                        () -> {
                                          threadsSeen.add(Thread.currentThread().getName());
                                          testing.runWithSpan("stage-2", () -> {});
                                          return a + "b";
                                        }))))
                    // hop to pool3
                    .flatMap(
                        fn1(
                            ab ->
                                pool3.apply(
                                    fn0(
                                        () -> {
                                          threadsSeen.add(Thread.currentThread().getName());
                                          testing.runWithSpan("stage-3", () -> {});
                                          return ab + "c";
                                        }))))
                    // terminal continuation — no FuturePool, Promise$Transformer only
                    .map(
                        fn1(
                            abc -> {
                              threadsSeen.add(Thread.currentThread().getName());
                              testing.runWithSpan("stage-4-terminal", () -> {});
                              return abc + "d";
                            })));

    assertThat(Await.result(chain, AWAIT)).isEqualTo("abcd");

    // none of the stages should have run on the JUnit caller thread
    assertThat(threadsSeen).noneMatch(n -> n.equals(callerThread));
    // each distinct pool must have actually serviced at least one stage
    assertThat(threadsSeen).anyMatch(n -> n.startsWith("pool-1"));
    assertThat(threadsSeen).anyMatch(n -> n.startsWith("pool-2"));
    assertThat(threadsSeen).anyMatch(n -> n.startsWith("pool-3"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("stage-1").hasParent(trace.getSpan(0)),
                span -> span.hasName("stage-2").hasParent(trace.getSpan(0)),
                span -> span.hasName("stage-3").hasParent(trace.getSpan(0)),
                span -> span.hasName("stage-4-terminal").hasParent(trace.getSpan(0))));
  }

  @Test
  void futurePoolInterleavedWithConstFuture() throws Exception {
    ExecutorService exec1 = Executors.newSingleThreadExecutor(named("interleave-1"));
    ExecutorService exec2 = Executors.newSingleThreadExecutor(named("interleave-2"));
    cleanup.deferCleanup(() -> shutdown(exec1));
    cleanup.deferCleanup(() -> shutdown(exec2));

    FuturePool pool1 = FuturePool$.MODULE$.apply(exec1);
    FuturePool pool2 = FuturePool$.MODULE$.apply(exec2);

    Future<String> chain =
        testing.runWithSpan(
            "parent",
            () ->
                pool1
                    .apply(
                        fn0(
                            () -> {
                              testing.runWithSpan("pool1-stage", () -> {});
                              return "a";
                            }))
                    // ConstFuture.flatMap exercises FutureInstrumentation.transform
                    .flatMap(
                        fn1(
                            a ->
                                Future.value(a + "b")
                                    .flatMap(
                                        fn1(
                                            ab -> {
                                              testing.runWithSpan("const-stage", () -> {});
                                              return pool2.apply(
                                                  fn0(
                                                      () -> {
                                                        testing.runWithSpan(
                                                            "pool2-stage", () -> {});
                                                        return ab + "c";
                                                      }));
                                            })))));

    assertThat(Await.result(chain, AWAIT)).isEqualTo("abc");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("pool1-stage").hasParent(trace.getSpan(0)),
                span -> span.hasName("const-stage").hasParent(trace.getSpan(0)),
                span -> span.hasName("pool2-stage").hasParent(trace.getSpan(0))));
  }

  @Test
  void futurePoolConcurrentChainsKeepContextsIsolated() throws Exception {
    ExecutorService exec = Executors.newFixedThreadPool(4, named("iso-pool"));
    cleanup.deferCleanup(() -> shutdown(exec));
    FuturePool pool = FuturePool$.MODULE$.apply(exec);

    int n = 8;
    CountDownLatch gate = new CountDownLatch(1);
    List<Future<Integer>> futures = new ArrayList<>(n);

    for (int i = 0; i < n; i++) {
      int idx = i;
      String parentName = "parent-" + idx;
      String childName = "child-" + idx;
      testing.runWithSpan(
          parentName,
          () -> {
            Future<Integer> f =
                pool.apply(
                    fn0(
                        () -> {
                          try {
                            gate.await();
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                          }
                          testing.runWithSpan(childName, () -> {});
                          return idx;
                        }));
            futures.add(f);
          });
    }

    // release all at once so they interleave on the pool threads
    gate.countDown();
    for (Future<Integer> f : futures) {
      Await.result(f, AWAIT);
    }

    // expect n traces, each with [parent-i, child-i]
    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      int idx = i;
      assertions.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent-" + idx).hasNoParent(),
                  span -> span.hasName("child-" + idx).hasParent(trace.getSpan(0))));
    }
    testing.waitAndAssertTraces(assertions);
  }

  @Test
  void localSchedulerReentrantSubmitPropagatesContext() throws Exception {
    // A respond callback re-scheduling work on the LocalScheduler forces
    // Activation.submit(Runnable) to fire while already on a scheduler thread.
    ExecutorService exec = Executors.newSingleThreadExecutor(named("ls-pool"));
    cleanup.deferCleanup(() -> shutdown(exec));
    FuturePool pool = FuturePool$.MODULE$.apply(exec);

    CountDownLatch nestedDone = new CountDownLatch(1);

    testing.runWithSpan(
        "parent",
        () -> {
          pool.apply(
              fn0(
                  () -> {
                    // on exec thread; scheduler is active here
                    Future.value("inner")
                        .respond(
                            fn1(
                                t -> {
                                  testing.runWithSpan("nested-child", () -> {});
                                  nestedDone.countDown();
                                  return BoxedUnit.UNIT;
                                }));
                    return BoxedUnit.UNIT;
                  }));
        });

    assertThat(nestedDone.await(5, SECONDS)).isTrue();
    assertParentAndChild("parent", "nested-child");
  }

  @Test
  void immediateFuturePoolStillPropagatesContext() throws Exception {
    FuturePool pool = FuturePool.immediatePool();

    Future<Integer> f =
        testing.runWithSpan(
            "parent",
            () ->
                pool.apply(
                        fn0(
                            () -> {
                              testing.runWithSpan("immediate-body", () -> {});
                              return 1;
                            }))
                    .map(
                        fn1(
                            v -> {
                              testing.runWithSpan("immediate-map", () -> {});
                              return v + 1;
                            })));

    assertThat((int) Await.result(f, AWAIT)).isEqualTo(2);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("immediate-body").hasParent(trace.getSpan(0)),
                span -> span.hasName("immediate-map").hasParent(trace.getSpan(0))));
  }

  private static void assertParentAndChild(String parentName, String childName) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName(parentName).hasNoParent(),
                span -> span.hasName(childName).hasParent(trace.getSpan(0))));
  }

  private static <T> Function0<T> fn0(Supplier<T> s) {
    return new AbstractFunction0<T>() {
      @Override
      public T apply() {
        return s.get();
      }
    };
  }

  private static <T, R> Function1<T, R> fn1(Function<T, R> f) {
    return new AbstractFunction1<T, R>() {
      @Override
      public R apply(T t) {
        return f.apply(t);
      }
    };
  }

  private static ThreadFactory named(String prefix) {
    AtomicInteger n = new AtomicInteger();
    return r -> {
      Thread t = new Thread(r, prefix + "-" + n.getAndIncrement());
      t.setDaemon(true);
      return t;
    };
  }

  private static void shutdown(ExecutorService exec) {
    exec.shutdownNow();
    try {
      exec.awaitTermination(5, SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
