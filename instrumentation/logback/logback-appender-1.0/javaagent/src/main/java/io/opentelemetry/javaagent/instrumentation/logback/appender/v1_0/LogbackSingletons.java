/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.appender.v1_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.List;

public final class LogbackSingletons {

  private static final LoggingEventMapper mapper;

  static {
    InstrumentationConfig config = InstrumentationConfig.get();

    boolean captureExperimentalAttributes =
        config.getBoolean(
            "otel.instrumentation.logback-appender.experimental-log-attributes", false);
    boolean captureCodeAttributes =
        config.getBoolean(
            "otel.instrumentation.logback-appender.experimental.capture-code-attributes", false);
    boolean captureMarkerAttributes =
        config.getBoolean(
            "otel.instrumentation.logback-appender.experimental.capture-marker-attributes", false);
    List<String> captureMdcAttributes =
        config.getList(
            "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes",
            emptyList());

    mapper =
        new LoggingEventMapper(
            captureExperimentalAttributes,
            captureMdcAttributes,
            captureCodeAttributes,
            captureMarkerAttributes);
  }

  public static LoggingEventMapper mapper() {
    return mapper;
  }

  private LogbackSingletons() {}
}
