/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

class LogEventToReplay implements LogEvent {

  private static final long serialVersionUID = -3639807661256104630L;

  private final LogEvent logEvent;
  private final String loggerName;
  private final Message message;
  private final Level level;
  private final Instant instant;
  private final Throwable thrown;
  private final Marker marker;
  private final ReadOnlyStringMap contextData;
  private final String threadName;
  private final long threadId;

  // Log4j 2 seems to reuse Message objects
  private static class MessageCopy implements Message {

    private static final long serialVersionUID = 6921483958779195777L;
    private final String formattedMessage;
    private final String format;
    private final Object[] parameters;
    private final Throwable throwable;

    public MessageCopy(Message message) {
      this.formattedMessage = message.getFormattedMessage();
      this.format = message.getFormat();
      this.parameters = message.getParameters();
      this.throwable = message.getThrowable();
    }

    @Override
    public String getFormattedMessage() {
      return formattedMessage;
    }

    @Override
    public String getFormat() {
      return format;
    }

    @Override
    public Object[] getParameters() {
      return parameters;
    }

    @Override
    public Throwable getThrowable() {
      return throwable;
    }
  }

  LogEventToReplay(LogEvent logEvent) {
    this.logEvent = logEvent;
    this.loggerName = logEvent.getLoggerName();
    Message messageOrigin = logEvent.getMessage();
    if (messageOrigin instanceof StructuredDataMessage) {
      StructuredDataMessage structuredDataMessage = (StructuredDataMessage) messageOrigin;
      this.message =
          new StructuredDataMessage(
              structuredDataMessage.getId(),
              structuredDataMessage.getFormat(),
              structuredDataMessage.getType(),
              structuredDataMessage.getData());
    } else if (messageOrigin instanceof StringMapMessage) {
      this.message = messageOrigin;
    } else {
      this.message = new MessageCopy(logEvent.getMessage());
    }

    this.level = logEvent.getLevel();
    this.instant = logEvent.getInstant();
    this.thrown = logEvent.getThrown();
    this.marker = logEvent.getMarker();
    this.contextData = logEvent.getContextData();
    this.threadName = logEvent.getThreadName();
    this.threadId = logEvent.getThreadId();
  }

  @Override
  public LogEvent toImmutable() {
    return logEvent.toImmutable();
  }

  @SuppressWarnings("deprecation") // Delegate
  @Override
  public Map<String, String> getContextMap() {
    return logEvent.getContextMap();
  }

  @Override
  public ReadOnlyStringMap getContextData() {
    return contextData;
  }

  @Override
  public ThreadContext.ContextStack getContextStack() {
    return logEvent.getContextStack();
  }

  @Override
  public String getLoggerFqcn() {
    return logEvent.getLoggerFqcn();
  }

  @Override
  public Level getLevel() {
    return level;
  }

  @Override
  public String getLoggerName() {
    return loggerName;
  }

  @Override
  public Marker getMarker() {
    return marker;
  }

  @Override
  public Message getMessage() {
    return message;
  }

  @Override
  public long getTimeMillis() {
    return logEvent.getTimeMillis();
  }

  @Override
  public Instant getInstant() {
    return instant;
  }

  @Override
  public StackTraceElement getSource() {
    return logEvent.getSource();
  }

  @Override
  public String getThreadName() {
    return threadName;
  }

  @Override
  public long getThreadId() {
    return threadId;
  }

  @Override
  public int getThreadPriority() {
    return logEvent.getThreadPriority();
  }

  @Override
  public Throwable getThrown() {
    return thrown;
  }

  @Override
  public ThrowableProxy getThrownProxy() {
    return logEvent.getThrownProxy();
  }

  @Override
  public boolean isEndOfBatch() {
    return logEvent.isEndOfBatch();
  }

  @Override
  public boolean isIncludeLocation() {
    return logEvent.isIncludeLocation();
  }

  @Override
  public void setEndOfBatch(boolean endOfBatch) {
    logEvent.setEndOfBatch(endOfBatch);
  }

  @Override
  public void setIncludeLocation(boolean locationRequired) {
    logEvent.setIncludeLocation(locationRequired);
  }

  @Override
  public long getNanoTime() {
    return logEvent.getNanoTime();
  }
}
