/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.v1_0;

import static io.opentelemetry.instrumentation.logback.v1_0.MdcPropertyMapHelper.instrument;
import static io.opentelemetry.instrumentation.logback.v1_0.MdcPropertyMapHelper.isInstrumented;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.util.Iterator;
import java.util.Map;

public class OpenTelemetryAppender extends UnsynchronizedAppenderBase<ILoggingEvent>
    implements AppenderAttachable<ILoggingEvent> {

  private final AppenderAttachableImpl<ILoggingEvent> aai = new AppenderAttachableImpl<>();

  public static ILoggingEvent wrapEvent(ILoggingEvent event) {
    SpanContext spanContext = Span.current().getSpanContext();
    if (!spanContext.isValid()) {
      return event;
    }

    Map<String, String> mdcPropertyMap = event.getMDCPropertyMap();
    if (isInstrumented(mdcPropertyMap)) {
      return event;
    }

    Map<String, String> instrumented = instrument(mdcPropertyMap, spanContext);
    return new LoggingEventWrapper(event, instrumented);
  }

  @Override
  protected void append(ILoggingEvent event) {
    aai.appendLoopOnAppenders(wrapEvent(event));
  }

  @Override
  public void addAppender(Appender<ILoggingEvent> appender) {
    aai.addAppender(appender);
  }

  @Override
  public Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
    return aai.iteratorForAppenders();
  }

  @Override
  public Appender<ILoggingEvent> getAppender(String name) {
    return aai.getAppender(name);
  }

  @Override
  public boolean isAttached(Appender<ILoggingEvent> appender) {
    return aai.isAttached(appender);
  }

  @Override
  public void detachAndStopAllAppenders() {
    aai.detachAndStopAllAppenders();
  }

  @Override
  public boolean detachAppender(Appender<ILoggingEvent> appender) {
    return aai.detachAppender(appender);
  }

  @Override
  public boolean detachAppender(String name) {
    return aai.detachAppender(name);
  }
}
