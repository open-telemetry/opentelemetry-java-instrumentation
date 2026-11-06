/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;

public class SynchronousLogRecordProcessorCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  private static volatile Runnable callback;

  public static void setCallback(Runnable callback) {
    SynchronousLogRecordProcessorCustomizerProvider.callback = callback;
  }

  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addLoggerProviderCustomizer(
        (loggerProvider, config) ->
            loggerProvider.addLogRecordProcessor(new SynchronousLogRecordProcessor()));
  }

  private static class SynchronousLogRecordProcessor implements LogRecordProcessor {

    @Override
    public void onEmit(Context context, ReadWriteLogRecord logRecord) {
      Runnable currentCallback = callback;
      if (currentCallback != null) {
        currentCallback.run();
      }
    }

    @Override
    public CompletableResultCode forceFlush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      return CompletableResultCode.ofSuccess();
    }
  }
}
