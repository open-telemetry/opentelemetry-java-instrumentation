/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v2_16;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.appender.GlobalLogEmitterProvider;
import io.opentelemetry.instrumentation.api.appender.LogBuilder;
import io.opentelemetry.instrumentation.log4j.appender.v2_16.internal.LogEventMapper;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.BiConsumer;

public final class Log4jHelper {

  public static void capture(Logger logger, Level level, Message message, Throwable throwable) {

    LogBuilder builder =
        GlobalLogEmitterProvider.get().logEmitterBuilder(logger.getName()).build().logBuilder();
    Map<String, String> contextData = ThreadContext.getImmutableContext();
    LogEventMapper.mapLogEvent(
        builder,
        message,
        level,
        throwable,
        null,
        contextData,
        contextData.isEmpty(),
        ContextDataMapper.INSTANCE);
    builder.emit();
  }

  private enum ContextDataMapper implements BiConsumer<AttributesBuilder, Map<String, String>> {
    INSTANCE;

    @Override
    public void accept(AttributesBuilder attributesBuilder, Map<String, String> contextData) {
      for (Map.Entry<String, String> entry : contextData.entrySet()) {
        attributesBuilder.put(LogEventMapper.getMdcAttributeKey(entry.getKey()), entry.getValue());
      }
    }
  }

  private Log4jHelper() {}
}
