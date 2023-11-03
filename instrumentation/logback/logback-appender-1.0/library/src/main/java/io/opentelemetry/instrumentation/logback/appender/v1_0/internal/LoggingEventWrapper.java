/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0.internal;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import java.util.List;
import java.util.Map;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class LoggingEventWrapper implements LoggingEventWithThreadId {

  private final ILoggingEvent loggingEvent;

  public LoggingEventWrapper(ILoggingEvent loggingEvent) {
    this.loggingEvent = loggingEvent;
  }

  @Override
  public String getThreadName() {
    return loggingEvent.getThreadName();
  }

  @Override
  public long getThreadId() {
    Thread currentThread = Thread.currentThread();
    return currentThread.getId();
  }

  @Override
  public Level getLevel() {
    return loggingEvent.getLevel();
  }

  @Override
  public String getMessage() {
    return loggingEvent.getMessage();
  }

  @Override
  public Object[] getArgumentArray() {
    return loggingEvent.getArgumentArray();
  }

  @Override
  public String getFormattedMessage() {
    return loggingEvent.getFormattedMessage();
  }

  @Override
  public String getLoggerName() {
    return loggingEvent.getLoggerName();
  }

  @Override
  public LoggerContextVO getLoggerContextVO() {
    return loggingEvent.getLoggerContextVO();
  }

  @Override
  public IThrowableProxy getThrowableProxy() {
    return loggingEvent.getThrowableProxy();
  }

  @Override
  public StackTraceElement[] getCallerData() {
    return loggingEvent.getCallerData();
  }

  @Override
  public boolean hasCallerData() {
    return loggingEvent.hasCallerData();
  }

  @SuppressWarnings("deprecation") // Delegate
  @Override
  public Marker getMarker() {
    return loggingEvent.getMarker();
  }

  @Override
  public List<Marker> getMarkerList() {
    return loggingEvent.getMarkerList();
  }

  @Override
  public Map<String, String> getMDCPropertyMap() {
    return loggingEvent.getMDCPropertyMap();
  }

  @SuppressWarnings("deprecation") // Delegate
  @Override
  public Map<String, String> getMdc() {
    return loggingEvent.getMdc();
  }

  @Override
  public long getTimeStamp() {
    return loggingEvent.getTimeStamp();
  }

  @Override
  public int getNanoseconds() {
    return loggingEvent.getNanoseconds();
  }

  @Override
  public long getSequenceNumber() {
    return loggingEvent.getSequenceNumber();
  }

  @Override
  public List<KeyValuePair> getKeyValuePairs() {
    return loggingEvent.getKeyValuePairs();
  }

  @Override
  public void prepareForDeferredProcessing() {
    loggingEvent.prepareForDeferredProcessing();
  }
}
