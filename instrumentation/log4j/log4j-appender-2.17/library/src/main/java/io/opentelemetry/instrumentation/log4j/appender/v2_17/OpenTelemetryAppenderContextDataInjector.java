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
import org.apache.logging.log4j.spi.CopyOnWrite;
import org.apache.logging.log4j.spi.DefaultThreadContextMap;
import org.apache.logging.log4j.spi.ReadOnlyThreadContextMap;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.StringMap;

public final class OpenTelemetryAppenderContextDataInjector implements ContextDataInjector {

  private final ContextDataInjector delegate = createDefaultInjector();

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
    return delegate.rawContextData();
  }

  private static ContextDataInjector createDefaultInjector() {
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
