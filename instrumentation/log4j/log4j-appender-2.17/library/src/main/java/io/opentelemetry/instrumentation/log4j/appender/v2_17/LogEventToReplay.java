/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static java.util.Collections.emptyMap;

import java.util.Map;
import javax.annotation.Nullable;
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
import org.apache.logging.log4j.util.SortedArrayStringMap;

class LogEventToReplay implements LogEvent {

  private static final long serialVersionUID = 1L;

  // Log4j 2 reuses LogEvent object, so we make a copy of all the fields that are used during export
  // in order to be able to replay the log event later.

  @Nullable private final String loggerName;
  private final Message message;
  private final Level level;
  private final Instant instant;
  @Nullable private final Throwable thrown;
  @Nullable private final Marker marker;
  private final ReadOnlyStringMap contextData;
  private final String threadName;
  private final long threadId;
  @Nullable private final StackTraceElement source;

  LogEventToReplay(LogEvent logEvent, boolean captureCodeAttributes) {
    this.loggerName = logEvent.getLoggerName();
    Message messageOrigin = logEvent.getMessage();
    if (messageOrigin instanceof StructuredDataMessage) {
      StructuredDataMessage structuredDataMessage = (StructuredDataMessage) messageOrigin;
      this.message =
          // Log4j 2 reuses StructuredDataMessage object
          new StructuredDataMessage(
              structuredDataMessage.getId(),
              structuredDataMessage.getFormat(),
              structuredDataMessage.getType(),
              structuredDataMessage.getData());
    } else if (messageOrigin instanceof StringMapMessage) {
      // StringMapMessage objects are not reused by Log4j 2
      this.message = messageOrigin;
    } else {
      this.message = new MessageCopy(logEvent.getMessage());
    }

    this.level = logEvent.getLevel();
    this.instant = logEvent.getInstant();
    this.thrown = logEvent.getThrown();
    this.marker = logEvent.getMarker();
    // copy context data, context data map may be reused
    this.contextData = new SortedArrayStringMap(logEvent.getContextData());
    this.threadName = logEvent.getThreadName();
    this.threadId = logEvent.getThreadId();
    this.source = captureCodeAttributes ? logEvent.getSource() : null;
  }

  @Override
  public LogEvent toImmutable() {
    return this;
  }

  @SuppressWarnings("deprecation") // Override
  @Override
  public Map<String, String> getContextMap() {
    return emptyMap();
  }

  @Override
  public ReadOnlyStringMap getContextData() {
    return contextData;
  }

  @Nullable
  @Override
  public ThreadContext.ContextStack getContextStack() {
    return null;
  }

  @Nullable
  @Override
  public String getLoggerFqcn() {
    return null;
  }

  @Override
  public Level getLevel() {
    return level;
  }

  @Nullable
  @Override
  public String getLoggerName() {
    return loggerName;
  }

  @Nullable
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
    return instant.getEpochMillisecond();
  }

  @Override
  public Instant getInstant() {
    return instant;
  }

  @Nullable
  @Override
  public StackTraceElement getSource() {
    return source;
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
    return 0;
  }

  @Nullable
  @Override
  public Throwable getThrown() {
    return thrown;
  }

  @Nullable
  @Override
  public ThrowableProxy getThrownProxy() {
    return null;
  }

  @Override
  public boolean isEndOfBatch() {
    return false;
  }

  @Override
  public boolean isIncludeLocation() {
    return false;
  }

  @Override
  public void setEndOfBatch(boolean endOfBatch) {}

  @Override
  public void setIncludeLocation(boolean locationRequired) {}

  @Override
  public long getNanoTime() {
    return 0;
  }

  private static class MessageCopy implements Message {

    private static final long serialVersionUID = 1L;
    private final String formattedMessage;
    private final String format;
    @Nullable private final Object[] parameters;
    @Nullable private final Throwable throwable;

    MessageCopy(Message message) {
      this.formattedMessage = message.getFormattedMessage();
      this.format = message.getFormat();
      Object[] parameters = message.getParameters();
      this.parameters = parameters == null ? null : parameters.clone();
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

    @Nullable
    @Override
    public Object[] getParameters() {
      return parameters == null ? null : parameters.clone();
    }

    @Nullable
    @Override
    public Throwable getThrowable() {
      return throwable;
    }
  }
}
