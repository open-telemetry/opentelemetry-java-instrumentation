/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_16;

import io.opentelemetry.instrumentation.api.appender.GlobalLogEmitterProvider;
import io.opentelemetry.instrumentation.api.appender.LogBuilder;
import io.opentelemetry.instrumentation.log4j.appender.v2_16.internal.ContextDataAccessor;
import io.opentelemetry.instrumentation.log4j.appender.v2_16.internal.LogEventMapper;
import java.io.Serializable;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

@Plugin(
    name = OpenTelemetryAppender.PLUGIN_NAME,
    category = Core.CATEGORY_NAME,
    elementType = Appender.ELEMENT_TYPE)
public class OpenTelemetryAppender extends AbstractAppender {

  static final String PLUGIN_NAME = "OpenTelemetry";

  private static final LogEventMapper<ReadOnlyStringMap> mapper =
      new LogEventMapper<>(ContextDataAccessorImpl.INSTANCE);

  @PluginBuilderFactory
  public static <B extends Builder<B>> B builder() {
    return new Builder<B>().asBuilder();
  }

  static class Builder<B extends Builder<B>> extends AbstractAppender.Builder<B>
      implements org.apache.logging.log4j.core.util.Builder<OpenTelemetryAppender> {

    @Override
    public OpenTelemetryAppender build() {
      return new OpenTelemetryAppender(
          getName(), getLayout(), getFilter(), isIgnoreExceptions(), getPropertyArray());
    }
  }

  private OpenTelemetryAppender(
      String name,
      Layout<? extends Serializable> layout,
      Filter filter,
      boolean ignoreExceptions,
      Property[] properties) {
    super(name, filter, layout, ignoreExceptions, properties);
  }

  @Override
  public void append(LogEvent event) {
    LogBuilder builder =
        GlobalLogEmitterProvider.get()
            .logEmitterBuilder(event.getLoggerName())
            .build()
            .logBuilder();
    ReadOnlyStringMap contextData = event.getContextData();
    mapper.mapLogEvent(
        builder,
        event.getMessage(),
        event.getLevel(),
        event.getThrown(),
        event.getInstant(),
        contextData);
    builder.emit();
  }

  private enum ContextDataAccessorImpl implements ContextDataAccessor<ReadOnlyStringMap> {
    INSTANCE;

    @Override
    @Nullable
    public Object getValue(ReadOnlyStringMap contextData, String key) {
      return contextData.getValue(key);
    }

    @Override
    public void forEach(ReadOnlyStringMap contextData, BiConsumer<String, Object> action) {
      contextData.forEach(action::accept);
    }
  }
}
