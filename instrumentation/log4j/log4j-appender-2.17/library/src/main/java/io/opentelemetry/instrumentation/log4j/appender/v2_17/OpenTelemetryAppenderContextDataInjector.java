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

public final class OpenTelemetryAppenderContextDataInjector implements ContextDataInjector {

  static final String DELEGATE_CONTEXT_DATA_INJECTOR_PROPERTY =
      "otel.instrumentation.log4j-appender.context-data-injector.delegate";

  private final ContextDataInjector delegate = createDelegateInjector();

  @Override
  public StringMap injectContextData(List<Property> properties, StringMap reusable) {
    StringMap contextData = delegate.injectContextData(properties, reusable);
    if (contextData.isFrozen()) {
      contextData = ContextDataFactory.createContextData(contextData);
    }
    contextData.putValue(OTEL_CONTEXT_DATA_KEY, Context.current());
    return contextData;
  }

  @Override
  public ReadOnlyStringMap rawContextData() {
    StringMap contextData = ContextDataFactory.createContextData(delegate.rawContextData());
    contextData.putValue(OTEL_CONTEXT_DATA_KEY, Context.current());
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
