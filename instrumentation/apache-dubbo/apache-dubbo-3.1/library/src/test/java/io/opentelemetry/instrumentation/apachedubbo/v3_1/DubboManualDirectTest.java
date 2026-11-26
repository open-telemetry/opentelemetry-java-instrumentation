/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v3_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.impl.HelloServiceImpl;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DubboManualDirectTest {

  private static final Queue<String> filterCalls = new ConcurrentLinkedQueue<>();

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void directReferencesAndServicesUseTheirOwnSdk() {
    DubboTelemetry first = DubboTelemetry.create(testing.getOpenTelemetry());
    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    cleanup.deferCleanup(tracerProvider);
    DubboTelemetry second =
        DubboTelemetry.builder(
                OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setPropagators(
                        ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build())
            .setClientSpanNameExtractorCustomizer(unused -> request -> "second client")
            .setServerSpanNameExtractorCustomizer(unused -> request -> "second server")
            .build();

    ServiceConfig<HelloService> firstService = newService("first", first);
    ServiceConfig<HelloService> secondService = newService("second", second);
    DubboBootstrap provider = newBootstrap("direct-provider");
    ProtocolConfig protocol = new ProtocolConfig("dubbo", PortUtils.findOpenPort());
    provider.protocol(protocol).service(firstService).service(secondService).start();

    ReferenceConfig<HelloService> firstReference = newReference(firstService, first);
    ReferenceConfig<HelloService> secondReference = newReference(secondService, second);
    newBootstrap("direct-consumer").reference(firstReference).reference(secondReference).start();

    // Both calls must use the same Dubbo extension instances but different tracing delegates.
    filterCalls.clear();
    assertThat(firstReference.get().hello("one")).isEqualTo("one");
    assertThat(secondReference.get().hello("two")).isEqualTo("two");
    assertThat(filterCalls)
        .containsExactly(
            "first", "second", "second", "first", "first", "second", "second", "first");

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span ->
                span.hasName(HelloService.class.getName() + "/hello")
                    .hasKind(SpanKind.CLIENT)
                    .hasNoParent(),
            span ->
                span.hasName(HelloService.class.getName() + "/hello")
                    .hasKind(SpanKind.SERVER)
                    .hasParent(trace.getSpan(0))));
    await()
        .untilAsserted(() ->
            assertThat(exporter.getFinishedSpanItems())
                .extracting(SpanData::getName)
                .containsExactlyInAnyOrder("second client", "second server"));
    SpanData client =
        exporter.getFinishedSpanItems().stream()
            .filter(span -> span.getKind() == SpanKind.CLIENT)
            .findFirst()
            .get();
    assertThat(exporter.getFinishedSpanItems())
        .filteredOn(span -> span.getKind() == SpanKind.SERVER)
        .singleElement()
        .satisfies(server ->
            assertThat(server.getParentSpanContext().getSpanId()).isEqualTo(client.getSpanId()));
  }

  private DubboBootstrap newBootstrap(String name) {
    ApplicationConfig application = new ApplicationConfig(name);
    application.setQosEnable(false);
    DubboBootstrap bootstrap = DubboBootstrap.newInstance(new FrameworkModel());
    cleanup.deferCleanup(bootstrap::destroy);
    return bootstrap.application(application).registry(new RegistryConfig("N/A"));
  }

  private static ServiceConfig<HelloService> newService(String group, DubboTelemetry telemetry) {
    ServiceConfig<HelloService> service = new ServiceConfig<>();
    service.setInterface(HelloService.class);
    service.setGroup(group);
    service.setRef(new HelloServiceImpl());
    service.setFilter("testSecond,default,testFirst");
    telemetry.instrument(service);
    telemetry.instrument(service);
    return service;
  }

  @SuppressWarnings("deprecation") // ReferenceConfig.setInjvm()
  private static ReferenceConfig<HelloService> newReference(
      ServiceConfig<HelloService> service, DubboTelemetry telemetry) {
    ReferenceConfig<HelloService> reference = new ReferenceConfig<>();
    reference.setInterface(HelloService.class);
    reference.setGroup(service.getGroup());
    reference.setUrl("dubbo://" + service.getExportedUrls().get(0).getAddress());
    reference.setInjvm(false);
    reference.setFilter("testFirst,default,testSecond");
    telemetry.instrument(reference);
    telemetry.instrument(reference);
    return reference;
  }

  public static final class FirstFilter implements Filter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
      filterCalls.add("first");
      return invoker.invoke(invocation);
    }
  }

  public static final class SecondFilter implements Filter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
      filterCalls.add("second");
      return invoker.invoke(invocation);
    }
  }
}
