/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_13_2;

import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.logging.LogSink;
import io.opentelemetry.sdk.logging.data.LogRecord;
import io.opentelemetry.sdk.resources.Resource;
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
  public static <B extends Builder<B>> B newBuilder() {
    return new Builder<B>().asBuilder();
  }

  public static class Builder<B extends Builder<B>> extends AbstractAppender.Builder<B>
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

  private final AtomicReference<SinkAndResource> sinkAndResourceRef = new AtomicReference<>();
  private final InstrumentationLibraryInfo instrumentationLibraryInfo;

  private OpenTelemetryAppender(
      String name,
      Layout<? extends Serializable> layout,
      Filter filter,
      boolean ignoreExceptions,
      Property[] properties) {
    super(name, filter, layout, ignoreExceptions, properties);
    instrumentationLibraryInfo =
        InstrumentationLibraryInfo.create(OpenTelemetryAppender.class.getName(), null);
  }

  @Override
  public void append(LogEvent event) {
    SinkAndResource sinkAndResource = sinkAndResourceRef.get();
    if (sinkAndResource == null) {
      // appender hasn't been initialized
      return;
    }
    LogRecord logRecord =
        LogEventMapper.toLogRecord(event, sinkAndResource.resource, instrumentationLibraryInfo);
    sinkAndResource.logSink.offer(logRecord);
  }

  void initialize(LogSink logSink, Resource resource) {
    if (!sinkAndResourceRef.compareAndSet(null, new SinkAndResource(logSink, resource))) {
      throw new IllegalStateException("OpenTelemetryAppender has already been initialized.");
    }
  }

  private static class SinkAndResource {
    private final LogSink logSink;
    private final Resource resource;

    private SinkAndResource(LogSink logSink, Resource resource) {
      this.logSink = logSink;
      this.resource = resource;
    }
  }
}
