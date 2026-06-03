/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.Experimental;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RpcExceptionEventExtractors {

  /**
   * Configures the RPC client exception event name and severity. Only takes effect when emitting
   * exceptions as logs is enabled via the {@code otel.semconv.exception.signal.preview} flag.
   */
  @CanIgnoreReturnValue
  public static <REQUEST, RESPONSE>
      InstrumenterBuilder<REQUEST, RESPONSE> setRpcClientExceptionEventExtractor(
          InstrumenterBuilder<REQUEST, RESPONSE> builder) {
    Experimental.setExceptionEventExtractor(
        builder,
        (logRecordBuilder, context, request) -> {
          logRecordBuilder.setEventName("rpc.client.call.exception");
          logRecordBuilder.setSeverity(Severity.WARN);
        });
    return builder;
  }

  /**
   * Configures the RPC server exception event name and severity. Only takes effect when emitting
   * exceptions as logs is enabled via the {@code otel.semconv.exception.signal.preview} flag.
   */
  @CanIgnoreReturnValue
  public static <REQUEST, RESPONSE>
      InstrumenterBuilder<REQUEST, RESPONSE> setRpcServerExceptionEventExtractor(
          InstrumenterBuilder<REQUEST, RESPONSE> builder) {
    Experimental.setExceptionEventExtractor(
        builder,
        (logRecordBuilder, context, request) -> {
          logRecordBuilder.setEventName("rpc.server.call.exception");
          logRecordBuilder.setSeverity(Severity.ERROR);
        });
    return builder;
  }

  private RpcExceptionEventExtractors() {}
}
