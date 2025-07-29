/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.mdc.v1_0;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class OpenTelemetryAppender extends UnsynchronizedAppenderBase<ILoggingEvent>
    implements AppenderAttachable<ILoggingEvent> {
  private static final Field MDC_MAP_FIELD;

  static {
    Field field;
    try {
      field = LoggingEvent.class.getDeclaredField("mdcPropertyMap");
      field.setAccessible(true);
    } catch (Exception exception) {
      field = null;
    }
    MDC_MAP_FIELD = field;
  }

  private boolean addBaggage;
  private String traceIdKey = LoggingContextConstants.TRACE_ID;
  private String spanIdKey = LoggingContextConstants.SPAN_ID;
  private String traceFlagsKey = LoggingContextConstants.TRACE_FLAGS;

  private final AppenderAttachableImpl<ILoggingEvent> aai = new AppenderAttachableImpl<>();

  /**
   * When set to true this will enable addition of all baggage entries to MDC. This can be done by
   * adding the following to the logback.xml config for this appender. {@code
   * <addBaggage>true</addBaggage>}
   *
   * @param addBaggage True if baggage should be added to MDC
   */
  public void setAddBaggage(boolean addBaggage) {
    this.addBaggage = addBaggage;
  }

  /** Customize MDC key name for the trace id. */
  public void setTraceIdKey(String traceIdKey) {
    this.traceIdKey = traceIdKey;
  }

  /** Customize MDC key name for the span id. */
  public void setSpanIdKey(String spanIdKey) {
    this.spanIdKey = spanIdKey;
  }

  /** Customize MDC key name for the trace flags. */
  public void setTraceFlagsKey(String traceFlagsKey) {
    this.traceFlagsKey = traceFlagsKey;
  }

  private void processEvent(ILoggingEvent event) {
    if (MDC_MAP_FIELD == null || event.getClass() != LoggingEvent.class) {
      return;
    }

    Map<String, String> eventContext = event.getMDCPropertyMap();
    if (eventContext != null && eventContext.containsKey(traceIdKey)) {
      // Assume already instrumented event if traceId is present.
      return;
    }

    Map<String, String> contextData = new HashMap<>();
    if (eventContext != null) {
      contextData.putAll(eventContext);
    }
    Context context = Context.current();
    Span currentSpan = Span.fromContext(context);

    if (currentSpan.getSpanContext().isValid()) {
      SpanContext spanContext = currentSpan.getSpanContext();
      contextData.put(traceIdKey, spanContext.getTraceId());
      contextData.put(spanIdKey, spanContext.getSpanId());
      contextData.put(traceFlagsKey, spanContext.getTraceFlags().asHex());
    }

    if (addBaggage) {
      Baggage baggage = Baggage.fromContext(context);
      baggage.forEach(
          (key, value) ->
              contextData.put(
                  // prefix all baggage values to avoid clashes with existing context
                  "baggage." + key, value.getValue()));
    }

    LoggerContextVO oldVo = event.getLoggerContextVO();
    LoggerContextVO vo =
        oldVo != null
            ? new LoggerContextVO(oldVo.getName(), contextData, oldVo.getBirthTime())
            : null;

    try {
      MDC_MAP_FIELD.set(event, contextData);
    } catch (IllegalAccessException ignored) {
      // setAccessible(true) was called on the field
    }
    ((LoggingEvent) event).setLoggerContextRemoteView(vo);
  }

  @Override
  protected void append(ILoggingEvent event) {
    processEvent(event);
    aai.appendLoopOnAppenders(event);
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
