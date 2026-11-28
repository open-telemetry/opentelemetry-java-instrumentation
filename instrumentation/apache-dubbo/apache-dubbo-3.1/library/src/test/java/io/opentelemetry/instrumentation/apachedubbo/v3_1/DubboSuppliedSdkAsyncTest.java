/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v3_1;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.AbstractDubboAsyncTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.junit.jupiter.api.extension.RegisterExtension;

class DubboSuppliedSdkAsyncTest extends AbstractDubboAsyncTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private DubboTelemetry telemetry;
  private DubboBootstrap providerBootstrap;
  private DubboBootstrap consumerBootstrap;

  @Override
  protected String instrumentationScopeName() {
    return "io.opentelemetry.apache-dubbo-3.1";
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void instrumentService(ServiceConfig<?> service) {
    telemetry().instrument(service);
  }

  @Override
  protected void instrumentReference(ReferenceConfig<?> reference) {
    telemetry().instrument(reference);
  }

  @Override
  protected void prepare() {
    providerBootstrap = newBootstrap("supplied-sdk-async-provider");
    consumerBootstrap = newBootstrap("supplied-sdk-async-consumer");
  }

  @Override
  protected void exportService(ServiceConfig<?> service, ProtocolConfig protocol) {
    providerBootstrap.protocol(protocol).service(service).start();
  }

  @Override
  protected void initializeReference(ReferenceConfig<?> reference) {
    consumerBootstrap.reference(reference).start();
  }

  @Override
  protected String serverLocalAddress(ServiceConfig<?> service) {
    return service.getExportedUrls().get(0).getHost();
  }

  @Override
  @SuppressWarnings("deprecation") // Check the completed invocation in Dubbo's service context.
  protected void completeProvider(Runnable completion) throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      executor
          .submit(() -> {
            completion.run();
            assertThat(RpcContext.getServiceContext().getInvocation()).isNull();
            assertThat(RpcContext.getServerAttachment().getObjectAttachments()).isEmpty();
            assertThat(RpcContext.getServerAttachment().getLocalAddress()).isNull();
          })
          .get(10, SECONDS);
    } finally {
      executor.shutdown();
    }
  }

  private DubboTelemetry telemetry() {
    if (telemetry == null) {
      telemetry = DubboTelemetry.create(testing.getOpenTelemetry());
    }
    return telemetry;
  }

  private DubboBootstrap newBootstrap(String name) {
    ApplicationConfig application = new ApplicationConfig(name);
    application.setQosEnable(false);
    DubboBootstrap bootstrap = DubboBootstrap.newInstance(new FrameworkModel());
    cleanup.deferCleanup(bootstrap::destroy);
    return bootstrap.application(application).registry(new RegistryConfig("N/A"));
  }
}
