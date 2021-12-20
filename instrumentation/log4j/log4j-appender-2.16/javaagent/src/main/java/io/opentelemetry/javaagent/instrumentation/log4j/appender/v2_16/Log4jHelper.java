/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v2_16;

import io.opentelemetry.instrumentation.api.appender.GlobalLogEmitterProvider;
import io.opentelemetry.instrumentation.api.appender.LogBuilder;
import io.opentelemetry.instrumentation.log4j.appender.v2_16.internal.LogEventMapper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;

public final class Log4jHelper {

  public static void capture(Logger logger, Level level, Message message, Throwable throwable) {

    LogBuilder builder =
        GlobalLogEmitterProvider.get().logEmitterBuilder(logger.getName()).build().logBuilder();

    LogEventMapper.setBody(builder, message);
    LogEventMapper.setSeverity(builder, level);
    LogEventMapper.setThrowable(builder, throwable);
    LogEventMapper.setContext(builder);

    builder.emit();
  }

  private Log4jHelper() {}
}
