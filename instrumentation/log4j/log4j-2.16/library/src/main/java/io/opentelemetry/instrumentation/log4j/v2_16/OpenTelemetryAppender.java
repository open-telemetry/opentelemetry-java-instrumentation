/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_16;

import io.opentelemetry.instrumentation.appender.api.LogBuilder;
import io.opentelemetry.instrumentation.appender.api.LogEmitterProvider;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;

@Plugin(
    name = OpenTelemetryAppender.PLUGIN_NAME,
    category = Core.CATEGORY_NAME,
    elementType = Appender.ELEMENT_TYPE)
public class OpenTelemetryAppender extends AbstractAppender {

  static final String PLUGIN_NAME = "OpenTelemetry";

  @PluginBuilderFactory
  public static <B extends Builder<B>> B builder() {
    return new Builder<B>().asBuilder();
  }

  static class Builder<B extends Builder<B>> extends AbstractAppender.Builder<B>
      implements org.apache.logging.log4j.core.util.Builder<OpenTelemetryAppender> {

    @Override
    public OpenTelemetryAppender build() {
      OpenTelemetryAppender appender =
          new OpenTelemetryAppender(
              getName(), getLayout(), getFilter(), isIgnoreExceptions(), getPropertyArray());
      OpenTelemetryLog4j.registerInstance(appender);
      return appender;
    }
  }

  private final AtomicReference<LogEmitterProvider> sdkLogEmitterProviderRef =
      new AtomicReference<>(LogEmitterProvider.noop());

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
    LogEmitterProvider logEmitterProvider = sdkLogEmitterProviderRef.get();
    LogBuilder builder =
        logEmitterProvider.logEmitterBuilder(event.getLoggerName()).build().logBuilder();
    LogEventMapper.mapLogEvent(builder, event);
    builder.emit();
  }

  void initialize(LogEmitterProvider logEmitterProvider) {
    if (!sdkLogEmitterProviderRef.compareAndSet(LogEmitterProvider.noop(), logEmitterProvider)) {
      throw new IllegalStateException("OpenTelemetryAppender has already been initialized.");
    }
  }
}
