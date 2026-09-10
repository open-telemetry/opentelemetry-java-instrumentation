/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.tracing.TraceContext;
import io.lettuce.core.tracing.TraceContextProvider;
import io.lettuce.core.tracing.TracerProvider;
import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.trace.SpanKind;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class LettuceNativeTracingReactiveTest extends AbstractLettuceClientTest {

  private static final Object USER_CONTEXT_KEY = new Object();
  private static final String USER_CONTEXT_VALUE = "user-context";

  private RedisReactiveCommands<String, String> reactiveCommands;
  private AsyncTraceContextProvider traceContextProvider;

  @BeforeEach
  void setUp() throws UnknownHostException {
    redisServer.start();
    cleanup.deferCleanup(redisServer::stop);

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    traceContextProvider = new AsyncTraceContextProvider();
    ClientResources resources =
        ClientResources.builder()
            .tracing(enabledTracing(Tracing.disabled(), traceContextProvider))
            .build();
    assertThat(resources.tracing().isEnabled()).isTrue();
    cleanup.deferCleanup(() -> resources.shutdown());

    redisClient = RedisClient.create(resources, embeddedDbUri);
    redisClient.setOptions(CLIENT_OPTIONS);
    cleanup.deferCleanup(redisClient::shutdown);
    connection = redisClient.connect();
    cleanup.deferCleanup(connection);
    reactiveCommands = connection.reactive();

    connection.sync().set("NATIVE_TRACING_KEY", "value");
    testing.waitForTraces(1);
    testing.clearData();
  }

  @Test
  void monoObservesCommandWithNativeTracing() {
    assertThat(reactiveCommands.get("NATIVE_TRACING_KEY").block()).isEqualTo("value");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)));
  }

  @Test
  void fluxObservesCommandWithNativeTracing() {
    List<String> keys = reactiveCommands.keys("NATIVE_TRACING_KEY").collectList().block();

    assertThat(keys).containsExactly("NATIVE_TRACING_KEY");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "KEYS " + host + ":" + port : "KEYS")
                        .hasKind(SpanKind.CLIENT)));
  }

  @Test
  void asyncTraceContextPreservesReactorContextAcrossResubscriptions() throws Exception {
    traceContextProvider.expectAsyncInvocations(2, Thread.currentThread());
    AtomicReference<List<String>> values = new AtomicReference<>();

    testing.runWithSpan(
        "parent",
        () ->
            values.set(
                reactiveCommands
                    .get("NATIVE_TRACING_KEY")
                    .repeat(1)
                    .contextWrite(context -> context.put(USER_CONTEXT_KEY, USER_CONTEXT_VALUE))
                    .collectList()
                    .block()));

    assertThat(values).hasValue(asList("value", "value"));
    assertThat(traceContextProvider.awaitInvocations()).isTrue();
    assertThat(traceContextProvider.invocationCount()).isEqualTo(2);
    assertThat(traceContextProvider.ranOnDifferentThread()).isTrue();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void cancellationBeforeAndAfterNativeSubscriptionDoesNotLeakSpans() throws Exception {
    traceContextProvider.blockAsyncInvocation();
    Disposable beforeNativeSubscription =
        reactiveCommands
            .keys("NATIVE_TRACING_KEY")
            .contextWrite(context -> context.put(USER_CONTEXT_KEY, USER_CONTEXT_VALUE))
            .subscribe();

    assertThat(traceContextProvider.awaitInvocation()).isTrue();
    beforeNativeSubscription.dispose();
    assertThat(traceContextProvider.awaitCancellation()).isTrue();
    assertThat(testing.spans()).isEmpty();

    traceContextProvider.useSynchronousProvider();
    connection.setAutoFlushCommands(false);
    cleanup.deferCleanup(() -> connection.setAutoFlushCommands(true));
    Disposable afterNativeSubscription =
        reactiveCommands
            .keys("NATIVE_TRACING_KEY")
            .contextWrite(context -> context.put(USER_CONTEXT_KEY, USER_CONTEXT_VALUE))
            .subscribe();
    afterNativeSubscription.dispose();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "KEYS " + host + ":" + port : "KEYS")
                        .hasKind(SpanKind.CLIENT)));
  }

  private static Tracing enabledTracing(
      Tracing delegate, TraceContextProvider traceContextProvider) {
    return new Tracing() {
      @Override
      public TracerProvider getTracerProvider() {
        return delegate.getTracerProvider();
      }

      @Override
      public TraceContextProvider initialTraceContextProvider() {
        return traceContextProvider;
      }

      @Override
      public boolean isEnabled() {
        return true;
      }

      @Override
      public boolean includeCommandArgsInSpanTags() {
        return delegate.includeCommandArgsInSpanTags();
      }

      @Override
      public Endpoint createEndpoint(SocketAddress address) {
        return delegate.createEndpoint(address);
      }
    };
  }

  private static final class AsyncTraceContextProvider implements TraceContextProvider {

    private final AtomicInteger invocationCount = new AtomicInteger();
    private final AtomicBoolean ranOnDifferentThread = new AtomicBoolean();
    private volatile CountDownLatch invocations = new CountDownLatch(0);
    private volatile CountDownLatch cancellation = new CountDownLatch(0);
    private volatile Thread callerThread;
    private volatile boolean block;
    private volatile boolean requireUserContext;
    private volatile boolean asynchronous;

    void expectAsyncInvocations(int count, Thread callerThread) {
      invocationCount.set(0);
      ranOnDifferentThread.set(false);
      invocations = new CountDownLatch(count);
      cancellation = new CountDownLatch(0);
      this.callerThread = callerThread;
      block = false;
      requireUserContext = true;
      asynchronous = true;
    }

    void blockAsyncInvocation() {
      invocationCount.set(0);
      invocations = new CountDownLatch(1);
      cancellation = new CountDownLatch(1);
      callerThread = Thread.currentThread();
      block = true;
      requireUserContext = true;
      asynchronous = true;
    }

    void useSynchronousProvider() {
      block = false;
      requireUserContext = false;
      asynchronous = false;
    }

    @Override
    public TraceContext getTraceContext() {
      return TraceContext.EMPTY;
    }

    @Override
    public Mono<TraceContext> getTraceContextLater() {
      Mono<TraceContext> traceContext =
          Mono.deferContextual(
              context -> {
                if (requireUserContext) {
                  Object userContext = context.getOrDefault(USER_CONTEXT_KEY, null);
                  assertThat(userContext).isEqualTo(USER_CONTEXT_VALUE);
                }
                invocationCount.incrementAndGet();
                ranOnDifferentThread.set(Thread.currentThread() != callerThread);
                invocations.countDown();
                if (block) {
                  return Mono.<TraceContext>never().doOnCancel(cancellation::countDown);
                }
                return Mono.just(TraceContext.EMPTY);
              });
      return asynchronous ? traceContext.subscribeOn(Schedulers.boundedElastic()) : traceContext;
    }

    boolean awaitInvocations() throws InterruptedException {
      return invocations.await(10, SECONDS);
    }

    boolean awaitInvocation() throws InterruptedException {
      return invocations.await(10, SECONDS);
    }

    boolean awaitCancellation() throws InterruptedException {
      return cancellation.await(10, SECONDS);
    }

    int invocationCount() {
      return invocationCount.get();
    }

    boolean ranOnDifferentThread() {
      return ranOnDifferentThread.get();
    }
  }
}
