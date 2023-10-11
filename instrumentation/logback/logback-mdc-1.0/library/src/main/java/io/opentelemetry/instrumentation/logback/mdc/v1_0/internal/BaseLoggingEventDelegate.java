/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.mdc.v1_0.internal;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import java.util.Map;
import org.slf4j.Marker;

/**
 * A convenience class that wraps a logging event and delegates all method calls to it. A subclass
 * can then override only what it needs.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public abstract class BaseLoggingEventDelegate implements ILoggingEvent {
  protected ILoggingEvent wrappedEvent;

  public BaseLoggingEventDelegate(ILoggingEvent event) {
    wrappedEvent = event;
  }

  @Override
  public String getThreadName() {
    return wrappedEvent.getThreadName();
  }

  @Override
  public Level getLevel() {
    return wrappedEvent.getLevel();
  }

  @Override
  public String getMessage() {
    return wrappedEvent.getMessage();
  }

  @Override
  public Object[] getArgumentArray() {
    return wrappedEvent.getArgumentArray();
  }

  @Override
  public String getFormattedMessage() {
    return wrappedEvent.getFormattedMessage();
  }

  @Override
  public String getLoggerName() {
    return wrappedEvent.getLoggerName();
  }

  @Override
  public LoggerContextVO getLoggerContextVO() {
    return wrappedEvent.getLoggerContextVO();
  }

  @Override
  public IThrowableProxy getThrowableProxy() {
    return wrappedEvent.getThrowableProxy();
  }

  @Override
  public StackTraceElement[] getCallerData() {
    return wrappedEvent.getCallerData();
  }

  @Override
  public boolean hasCallerData() {
    return wrappedEvent.hasCallerData();
  }

  @Override
  public Marker getMarker() {
    return wrappedEvent.getMarker();
  }

  @Override
  public Map<String, String> getMDCPropertyMap() {
    return wrappedEvent.getMDCPropertyMap();
  }

  @Override
  // We are forced to implement this as it's part of the interface
  @SuppressWarnings("deprecation")
  public Map<String, String> getMdc() {
    return this.getMDCPropertyMap();
  }

  @Override
  public long getTimeStamp() {
    return wrappedEvent.getTimeStamp();
  }

  @Override
  public void prepareForDeferredProcessing() {
    wrappedEvent.prepareForDeferredProcessing();
  }
}
