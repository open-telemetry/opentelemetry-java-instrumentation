/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static java.util.Collections.emptyList;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.opentelemetry.instrumentation.api.appender.internal.LogEmitterProvider;
import io.opentelemetry.instrumentation.api.appender.internal.LogEmitterProviderHolder;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import io.opentelemetry.instrumentation.sdk.appender.internal.DelegatingLogEmitterProvider;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.MDC;

public class OpenTelemetryAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private static final LogEmitterProviderHolder logEmitterProviderHolder =
      new LogEmitterProviderHolder();

  private static volatile boolean captureExperimentalAttributes = false;
  private static volatile List<String> captureMdcAttributes = emptyList();
  private static volatile boolean captureAllMdcAttributes = false;

  private final Object mapperLock = new Object();
  // lazy initialized
  private volatile LoggingEventMapper mapper;

  public OpenTelemetryAppender() {}

  @Override
  protected void append(ILoggingEvent event) {
    mapper().emit(logEmitterProviderHolder.get(), event);
  }

  private LoggingEventMapper mapper() {
    LoggingEventMapper m = mapper;
    if (m == null) {
      synchronized (mapperLock) {
        m = mapper;
        if (m == null) {
          mapper =
              m =
                  new LoggingEventMapper(
                      captureExperimentalAttributes, captureMdcAttributes, captureAllMdcAttributes);
        }
      }
    }
    return m;
  }

  /**
   * This should be called once as early as possible in your application initialization logic, often
   * in a {@code static} block in your main class. It should only be called once - an attempt to
   * call it a second time will result in an error. If trying to set the {@link
   * SdkLogEmitterProvider} multiple times in tests, use {@link
   * OpenTelemetryAppender#resetSdkLogEmitterProviderForTest()} between them.
   */
  public static void setSdkLogEmitterProvider(SdkLogEmitterProvider sdkLogEmitterProvider) {
    logEmitterProviderHolder.set(DelegatingLogEmitterProvider.from(sdkLogEmitterProvider));
  }

  /**
   * Sets whether experimental attributes should be set to logs. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions.
   */
  public static void setCaptureExperimentalAttributes(boolean captureExperimentalAttributes) {
    OpenTelemetryAppender.captureExperimentalAttributes = captureExperimentalAttributes;
  }

  /** Configures the {@link MDC} attributes that will be copied to logs. */
  public static void setCapturedMdcAttributes(Collection<String> captureMdcAttributes) {
    OpenTelemetryAppender.captureMdcAttributes = new ArrayList<>(captureMdcAttributes);
  }

  /**
   * Sets whether all log4j {@link MDC} attributes should be copied to logs. This setting overrides
   * the attributes list set in {@link #setCapturedMdcAttributes(Collection)}.
   */
  public static void setCaptureAllMdcAttributes(boolean captureAllMdcAttributes) {
    OpenTelemetryAppender.captureAllMdcAttributes = captureAllMdcAttributes;
  }

  /**
   * Unsets the global {@link LogEmitterProvider} and the appender configuration. This is only meant
   * to be used from tests which need to reconfigure the appender.
   */
  public static void resetForTest() {
    logEmitterProviderHolder.resetForTest();

    captureExperimentalAttributes = false;
    captureMdcAttributes = emptyList();
    captureAllMdcAttributes = false;
  }

  /**
   * Unsets the global {@link LogEmitterProvider}. This is only meant to be used from tests which
   * need to reconfigure {@link LogEmitterProvider}.
   *
   * @deprecated Use {@link #resetForTest()} instead.
   */
  @Deprecated
  public static void resetSdkLogEmitterProviderForTest() {
    logEmitterProviderHolder.resetForTest();
  }
}
