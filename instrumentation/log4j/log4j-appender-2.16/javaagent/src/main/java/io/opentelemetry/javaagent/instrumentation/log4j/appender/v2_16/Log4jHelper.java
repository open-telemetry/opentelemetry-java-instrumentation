/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v2_16;

import io.opentelemetry.instrumentation.api.appender.internal.AgentLogEmitterProvider;
import io.opentelemetry.instrumentation.api.appender.internal.LogBuilder;
import io.opentelemetry.instrumentation.log4j.appender.v2_16.internal.ContextDataAccessor;
import io.opentelemetry.instrumentation.log4j.appender.v2_16.internal.LogEventMapper;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.Message;

public final class Log4jHelper {

  private static final LogEventMapper<Map<String, String>> mapper =
      new LogEventMapper<>(ContextDataAccessorImpl.INSTANCE);

  public static void capture(Logger logger, Level level, Message message, Throwable throwable) {

    LogBuilder builder =
        AgentLogEmitterProvider.get().logEmitterBuilder(logger.getName()).build().logBuilder();
    Map<String, String> contextData = ThreadContext.getImmutableContext();
    mapper.mapLogEvent(builder, message, level, throwable, null, contextData);
    builder.emit();
  }

  private enum ContextDataAccessorImpl implements ContextDataAccessor<Map<String, String>> {
    INSTANCE;

    @Override
    @Nullable
    public Object getValue(Map<String, String> contextData, String key) {
      return contextData.get(key);
    }

    @Override
    public void forEach(Map<String, String> contextData, BiConsumer<String, Object> action) {
      contextData.forEach(action);
    }
  }

  private Log4jHelper() {}
}
