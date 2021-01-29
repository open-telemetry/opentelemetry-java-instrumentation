/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetrysdk;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerManagement;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopTracerManagement implements SdkTracerManagement {

  private static final Logger log = LoggerFactory.getLogger(NoopTracerManagement.class);

  private static final AtomicBoolean messageAlreadyLogged = new AtomicBoolean();

  public static final NoopTracerManagement INSTANCE = new NoopTracerManagement();

  public static void logCannotUseTracerManagementWarning() {
    if (!messageAlreadyLogged.getAndSet(true)) {
      String message =
          "direct usage of the OpenTelemetry SDK, e.g. using OpenTelemetrySdk.getGlobalTracerManagement()"
              + " is not supported when running agent (see https://github.com/open-telemetry/opentelemetry-java-instrumentation#troubleshooting"
              + " for how to run with debug logging, which will log stack trace with this message)."
              + " Returning a no-op management interface.";
      if (log.isDebugEnabled()) {
        log.debug(message, new Exception("stack trace"));
      } else {
        log.info(message);
      }
    }
  }

  @Override
  public TraceConfig getActiveTraceConfig() {
    return TraceConfig.getDefault();
  }

  @Override
  public CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode forceFlush() {
    return CompletableResultCode.ofSuccess();
  }
}
