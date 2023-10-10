/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-spans.md">RPC
 * server attributes</a>.
 *
 * <p>This class delegates to a type-specific {@link RpcAttributesGetter} for individual attribute
 * extraction from request/response objects.
 */
public final class RpcServerAttributesExtractor<REQUEST, RESPONSE>
    extends RpcCommonAttributesExtractor<REQUEST, RESPONSE> implements SpanKeyProvider {

  /** Creates the RPC server attributes extractor. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      RpcAttributesGetter<REQUEST> getter) {
    return new RpcServerAttributesExtractor<>(getter);
  }

  private RpcServerAttributesExtractor(RpcAttributesGetter<REQUEST> getter) {
    super(getter);
  }

  /**
   * This method is internal and is hence not for public use. Its API is unstable and can change at
   * any time.
   */
  @Override
  public SpanKey internalGetSpanKey() {
    return SpanKey.RPC_SERVER;
  }
}
