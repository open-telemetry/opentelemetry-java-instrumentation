/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.instrumentation.log4j.appender.v2_17.internal.ContextDataKeys.OTEL_CONTEXT_DATA_KEY;

import io.opentelemetry.context.Context;
import java.util.List;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.ContextDataInjector;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.ThreadContextDataInjector;
import org.apache.logging.log4j.core.util.Loader;
import org.apache.logging.log4j.spi.CopyOnWrite;
import org.apache.logging.log4j.spi.DefaultThreadContextMap;
import org.apache.logging.log4j.spi.ReadOnlyThreadContextMap;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.StringMap;

/**
 * Injects the full OpenTelemetry {@link Context} into Log4j event context data for async logger
 * handoff.
 *
 * <p>This uses Log4j's ContextDataInjector extension point instead of ContextDataProvider because
 * the appender needs to carry the {@link Context} object itself, not only string key/value pairs.
 * Although Log4j 2.17 added ContextDataProvider.supplyStringMap(), Log4j does not call it for every
 * thread context map. In web-app mode, which is enabled by default when the Servlet API is on the
 * classpath, Log4j disables thread locals and uses DefaultThreadContextMap. That path calls
 * ContextDataProvider.supplyContextData(), which is limited to Map&lt;String, String&gt;.
 *
 * <p>By delegating to Log4j's selected injector first and then adding the {@link Context} object to
 * the resulting {@link StringMap}, this works for DefaultThreadContextMap, copy-on-write, and
 * garbage-free thread context maps.
 */
public final class OpenTelemetryAppenderContextDataInjector implements ContextDataInjector {

  static final String DELEGATE_CONTEXT_DATA_INJECTOR_PROPERTY =
      "otel.instrumentation.log4j-appender.context-data-injector.delegate";

  private final ContextDataInjector delegate = createDelegateInjector();

  @Override
  public StringMap injectContextData(List<Property> properties, StringMap reusable) {
    Context context = Context.current();
    if (context == Context.root()) {
      return delegate.injectContextData(properties, reusable);
    }
    StringMap contextData = delegate.injectContextData(properties, reusable);
    if (contextData.isFrozen()) {
      contextData = ContextDataFactory.createContextData(contextData);
    }
    contextData.putValue(OTEL_CONTEXT_DATA_KEY, context);
    return contextData;
  }

  @Override
  public ReadOnlyStringMap rawContextData() {
    Context context = Context.current();
    if (context == Context.root()) {
      return delegate.rawContextData();
    }
    StringMap contextData = ContextDataFactory.createContextData(delegate.rawContextData());
    contextData.putValue(OTEL_CONTEXT_DATA_KEY, context);
    return contextData;
  }

  private static ContextDataInjector createDelegateInjector() {
    String className =
        PropertiesUtil.getProperties().getStringProperty(DELEGATE_CONTEXT_DATA_INJECTOR_PROPERTY);
    if (className != null) {
      try {
        if (className.equals(OpenTelemetryAppenderContextDataInjector.class.getName())) {
          throw new IllegalArgumentException("Delegate injector cannot be this injector");
        }
        return Loader.newCheckedInstanceOf(className, ContextDataInjector.class);
      } catch (Exception e) {
        StatusLogger.getLogger()
            .warn(
                "Could not create configured ContextDataInjector delegate '{}'; ignoring delegate override",
                className,
                e);
      }
    }

    // Mirror Log4j's ContextDataInjectorFactory#createDefaultInjector() selection.
    ReadOnlyThreadContextMap threadContextMap = ThreadContext.getThreadContextMap();
    if (threadContextMap == null || threadContextMap instanceof DefaultThreadContextMap) {
      return new ThreadContextDataInjector.ForDefaultThreadContextMap();
    }
    if (threadContextMap instanceof CopyOnWrite) {
      return new ThreadContextDataInjector.ForCopyOnWriteThreadContextMap();
    }
    return new ThreadContextDataInjector.ForGarbageFreeThreadContextMap();
  }
}
