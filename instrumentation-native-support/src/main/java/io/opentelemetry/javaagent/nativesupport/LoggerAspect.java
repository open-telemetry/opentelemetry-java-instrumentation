/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.nativesupport;

import com.oracle.svm.core.annotate.Advice;
import com.oracle.svm.core.annotate.Aspect;
import com.oracle.svm.core.annotate.Aspect.JDKClassOnly;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.instrumentation.jul.JavaUtilLoggingHelper;
import io.opentelemetry.api.logs.LoggerProvider;

import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Match all subclasses of java.util.logging.Logger.
 * This is the native image version of {@link io.opentelemetry.javaagent.instrumentation.jul.JavaUtilLoggingInstrumentationModule}.
 */
@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
@Aspect(subClassOf = "java.util.logging.Logger", onlyWith = JDKClassOnly.class)
final public class LoggerAspect {
  @SuppressWarnings("EmptyCatch")
  @Advice.Before("log")
  public static CallDepth beforeLog(LogRecord logRecord, @Advice.This Logger logger) {
    CallDepth otelCallDepth = null;
    try {
      otelCallDepth = CallDepth.forClass(LoggerProvider.class);
      if (otelCallDepth.getAndIncrement() == 0) {
        JavaUtilLoggingHelper.capture(logger, logRecord);
      }
    } catch (Throwable throwable) {

    }
    return otelCallDepth;
  }

  @SuppressWarnings("EmptyCatch")
  @Advice.After(value = "log", onThrowable = Throwable.class)
  public static void afterLog(LogRecord logRecord, @Advice.BeforeResult CallDepth otelCallDepth) {
    try {
      otelCallDepth.decrementAndGet();
    } catch (Throwable t) {

    }
  }

}
