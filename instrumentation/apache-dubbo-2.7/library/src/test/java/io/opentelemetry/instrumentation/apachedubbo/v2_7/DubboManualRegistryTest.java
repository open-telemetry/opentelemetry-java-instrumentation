/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class DubboManualRegistryTest extends AbstractDubboRegistryTest {

  private static final String CLIENT_FILTER_NAME = "otelManualClient";
  private static final String SERVER_FILTER_NAME = "otelManualServer";

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static OpenTelemetry originalGlobalOpenTelemetry;
  private static DubboTelemetry dubboTelemetry;

  @BeforeAll
  static void registerFilters() {
    originalGlobalOpenTelemetry = GlobalOpenTelemetry.get();
    dubboTelemetry = DubboTelemetry.create(testing.getOpenTelemetry());

    ExtensionLoader<Filter> filterLoader = ExtensionLoader.getExtensionLoader(Filter.class);
    filterLoader.addExtension(CLIENT_FILTER_NAME, ManualClientFilter.class);
    filterLoader.addExtension(SERVER_FILTER_NAME, ManualServerFilter.class);

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
    service.setFilter(SERVER_FILTER_NAME);
  }

  @Override
  protected void configureReference(ReferenceConfig<?> referenceConfig) {
    referenceConfig.setFilter(CLIENT_FILTER_NAME);
  }

  @Override
  protected <T, E extends Throwable> T runWithParentSpan(String name, ThrowingSupplier<T, E> action)
      throws E {
    Span span =
        testing.getOpenTelemetry().getTracer("test.manual-dubbo").spanBuilder(name).startSpan();
    try (Scope ignored = span.makeCurrent()) {
      return action.get();
    } finally {
      span.end();
    }
  }

  public static final class ManualClientFilter implements Filter {

    private final Filter delegate = dubboTelemetry.newClientFilter();

    public ManualClientFilter() {}

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
      return delegate.invoke(invoker, invocation);
    }
  }

  public static final class ManualServerFilter implements Filter {

    private final Filter delegate = dubboTelemetry.newServerFilter();

    public ManualServerFilter() {}

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
      return delegate.invoke(invoker, invocation);
    }
  }
}
