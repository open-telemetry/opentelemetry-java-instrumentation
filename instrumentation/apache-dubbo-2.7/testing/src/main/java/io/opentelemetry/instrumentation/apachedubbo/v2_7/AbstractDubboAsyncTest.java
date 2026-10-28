/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class AbstractDubboAsyncTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected abstract InstrumentationExtension testing();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings({
    "InterruptedExceptionSwallowed", // JUnit handles test exceptions.
    "deprecation" // Dubbo 2.7 configuration API
  })
  void delayedProviderCompletion(boolean fail) throws Exception {
    int port = PortUtils.findOpenPort();
    DelayedServiceImpl provider = new DelayedServiceImpl();
    cleanup.deferCleanup(() -> provider.result.complete("cleanup"));
    ProtocolConfig protocol = new ProtocolConfig("dubbo", port);
    protocol.setHost("127.0.0.1");
    protocol.setThreadpool("fixed");
    protocol.setThreads(1);
    protocol.setQueues(1);

    ServiceConfig<DelayedService> service = new ServiceConfig<>();
    cleanup.deferCleanup(service::unexport);
    ApplicationConfig application = new ApplicationConfig("delayed-provider");
    application.setQosEnable(false);
    service.setApplication(application);
    service.setRegistry(new RegistryConfig("N/A"));
    service.setProtocol(protocol);
    service.setInterface(DelayedService.class);
    service.setRef(provider);
    service.export();

    ReferenceConfig<DelayedService> reference = new ReferenceConfig<>();
    cleanup.deferCleanup(reference::destroy);
    reference.setApplication(application);
    reference.setInterface(DelayedService.class);
    reference.setUrl("dubbo://localhost:" + port + "/?timeout=30000");
    reference.setInjvm(false);
    reference.setRetries(0);
    DelayedService client = reference.get();

    CompletableFuture<String> response = client.hello();
    Span serverSpan = provider.serverSpan.get(10, SECONDS);
    // With one provider thread, the barrier cannot run until hello's filter chain has returned.
    assertThat(client.barrier()).isEqualTo("barrier");
    testing().waitAndAssertTraces(trace -> assertCall(trace, "barrier", false));
    assertThat(response).isNotDone();
    assertThat(serverSpan.isRecording()).isTrue();

    if (fail) {
      provider.result.completeExceptionally(new IllegalStateException("delayed failure"));
      assertThatThrownBy(response::join)
          .hasCauseInstanceOf(IllegalStateException.class)
          .hasMessageContaining("delayed failure");
    } else {
      provider.result.complete("hello");
      assertThat(response.get(10, SECONDS)).isEqualTo("hello");
    }

    testing()
        .waitAndAssertSortedTraces(
            Comparator.comparing(trace -> trace.get(0).getName()),
            trace -> assertCall(trace, "barrier", false),
            trace -> assertCall(trace, "hello", fail));
    assertThat(serverSpan.isRecording()).isFalse();

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.apache-dubbo-2.7",
            metric ->
                metric
                    .hasName("rpc.server.call.duration")
                    .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                    .hasUnit("s")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasBucketBoundaries(
                                            0.0001, 0.0005, 0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0,
                                            5.0, 10.0, 50.0, 100.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(RPC_SYSTEM_NAME, "dubbo"),
                                            equalTo(
                                                RPC_METHOD,
                                                DelayedService.class.getName() + "/barrier")),
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasBucketBoundaries(
                                            0.0001, 0.0005, 0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0,
                                            5.0, 10.0, 50.0, 100.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(RPC_SYSTEM_NAME, "dubbo"),
                                            equalTo(
                                                RPC_METHOD,
                                                DelayedService.class.getName() + "/hello"),
                                            equalTo(
                                                ERROR_TYPE,
                                                fail
                                                    ? IllegalStateException.class.getName()
                                                    : null)))));
  }

  private static void assertCall(TraceAssert trace, String method, boolean fail) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName(DelayedService.class.getName() + "/" + method)
                .hasKind(SpanKind.CLIENT)
                .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                .hasNoParent(),
        span ->
            span.hasName(DelayedService.class.getName() + "/" + method)
                .hasKind(SpanKind.SERVER)
                .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                .hasParent(trace.getSpan(0))
                .hasStatus(fail ? StatusData.error() : StatusData.unset())
                .hasAttributesSatisfyingExactly(
                    equalTo(RPC_SYSTEM_NAME, "dubbo"),
                    equalTo(RPC_METHOD, DelayedService.class.getName() + "/" + method),
                    satisfies(NETWORK_PEER_ADDRESS, val -> val.isInstanceOf(String.class)),
                    satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
                    satisfies(NETWORK_TYPE, val -> val.isIn(null, "ipv4", "ipv6")),
                    equalTo(ERROR_TYPE, fail ? IllegalStateException.class.getName() : null)));
  }

  public interface DelayedService {
    CompletableFuture<String> hello();

    String barrier();
  }

  public static class DelayedServiceImpl implements DelayedService {
    private final CompletableFuture<String> result = new CompletableFuture<>();
    private final CompletableFuture<Span> serverSpan = new CompletableFuture<>();

    @Override
    public CompletableFuture<String> hello() {
      serverSpan.complete(Span.current());
      return result;
    }

    @Override
    public String barrier() {
      return "barrier";
    }
  }
}
