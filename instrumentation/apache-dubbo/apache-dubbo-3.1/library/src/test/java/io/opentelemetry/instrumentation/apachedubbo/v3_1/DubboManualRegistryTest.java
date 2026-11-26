/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v3_1;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.AbstractDubboRegistryTest;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.registry.client.InstanceAddressURL;
import org.apache.dubbo.registry.client.ServiceDiscoveryRegistryDirectory;
import org.apache.dubbo.registry.client.migration.MigrationInvoker;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DubboManualRegistryTest extends AbstractDubboRegistryTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension discoveryCleanup = AutoCleanupExtension.create();

  private static OpenTelemetry originalGlobalOpenTelemetry;
  private static DubboTelemetry telemetry;

  @BeforeAll
  static void configureTelemetry() {
    originalGlobalOpenTelemetry = GlobalOpenTelemetry.get();
    telemetry = DubboTelemetry.create(testing.getOpenTelemetry());
    GlobalOpenTelemetry.resetForTest();
    GlobalOpenTelemetry.set(OpenTelemetry.noop());
  }

  @AfterAll
  static void restoreGlobalOpenTelemetry() {
    GlobalOpenTelemetry.resetForTest();
    GlobalOpenTelemetry.set(originalGlobalOpenTelemetry);
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configureService(ServiceConfig<?> service) {
    telemetry.instrument(service);
    telemetry.instrument(service);
  }

  @Override
  protected void configureReference(ReferenceConfig<?> referenceConfig) {
    telemetry.instrument(referenceConfig);
    telemetry.instrument(referenceConfig);
  }

  @Test
  @SuppressWarnings("deprecation") // ReferenceConfig.setInjvm() and getInvoker()
  void applicationDiscoveryAfterProviderRefresh() {
    DubboBootstrap first = startDiscoveryProvider("first");

    ReferenceConfig<HelloService> reference = new ReferenceConfig<>();
    reference.setInterface(HelloService.class);
    reference.setProvidedBy("manual-discovery-first,manual-discovery-replacement");
    reference.setParameters(singletonMap("migration.step", "FORCE_APPLICATION"));
    reference.setInjvm(false);
    reference.setTimeout(30000);
    configureReference(reference);
    newBootstrap("manual-discovery-consumer").reference(reference).start();
    HelloService proxy = reference.get();

    MigrationInvoker<?> migration = (MigrationInvoker<?>) reference.getInvoker();
    Directory<?> directory = migration.getServiceDiscoveryInvoker().getDirectory();
    assertThat(directory).isInstanceOf(ServiceDiscoveryRegistryDirectory.class);
    assertThat(directory.getAllInvokers())
        .singleElement()
        .satisfies(invoker -> assertThat(invoker.getUrl()).isInstanceOf(InstanceAddressURL.class));
    assertThat(proxy.hello("ignored")).isEqualTo("first");
    assertDiscoveryTrace();
    testing.clearData();

    int oldPort = directory.getAllInvokers().get(0).getUrl().getPort();
    startDiscoveryProvider("replacement");
    first.destroy();
    await()
        .untilAsserted(() ->
            assertThat(directory.getAllInvokers())
                .singleElement()
                .satisfies(
                    invoker -> assertThat(invoker.getUrl().getPort()).isNotEqualTo(oldPort)));

    assertThat(proxy.hello("ignored")).isEqualTo("replacement");
    assertDiscoveryTrace();
  }

  private static void assertDiscoveryTrace() {
    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span ->
                span.hasName(HelloService.class.getName() + "/hello")
                    .hasKind(SpanKind.CLIENT)
                    .hasNoParent()
                    .hasAttribute(SERVER_ADDRESS, zkAddress() + "/" + HelloService.class.getName()),
            span ->
                span.hasName(HelloService.class.getName() + "/hello")
                    .hasKind(SpanKind.SERVER)
                    .hasParent(trace.getSpan(0))));
  }

  private DubboBootstrap startDiscoveryProvider(String response) {
    ServiceConfig<HelloService> service = new ServiceConfig<>();
    service.setInterface(HelloService.class);
    service.setRef(unused -> response);
    configureService(service);
    ProtocolConfig protocol = new ProtocolConfig("dubbo", PortUtils.findOpenPort());
    return newBootstrap("manual-discovery-" + response).protocol(protocol).service(service).start();
  }

  private DubboBootstrap newBootstrap(String name) {
    ApplicationConfig application = new ApplicationConfig(name);
    application.setQosEnable(false);
    DubboBootstrap bootstrap = DubboBootstrap.newInstance(new FrameworkModel());
    discoveryCleanup.deferCleanup(bootstrap::destroy);
    return bootstrap.application(application).registry(newZkRegistryConfig(zkAddress()));
  }
}
