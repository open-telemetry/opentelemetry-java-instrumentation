/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.ExceptionEventExtractor;

/**
 * {@link ExceptionEventExtractor} constants for RPC client and server instrumentations.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class RpcExceptionEventExtractors {

  /** Exception event extractor for RPC client spans. */
  public static <REQUEST> ExceptionEventExtractor<REQUEST> client() {
    return ExceptionEventExtractor.create("rpc.client.call.exception", Severity.WARN);
  }

  /** Exception event extractor for RPC server spans. */
  public static <REQUEST> ExceptionEventExtractor<REQUEST> server() {
    return ExceptionEventExtractor.create("rpc.server.call.exception", Severity.ERROR);
  }

  private RpcExceptionEventExtractors() {}
}
