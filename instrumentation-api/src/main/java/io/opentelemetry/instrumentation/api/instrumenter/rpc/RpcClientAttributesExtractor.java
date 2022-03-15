/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import io.opentelemetry.instrumentation.api.annotations.UnstableApi;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/rpc.md">RPC
 * client attributes</a>.
 *
 * <p>This class delegates to a type-specific {@link RpcAttributesGetter} for individual attribute
 * extraction from request/response objects.
 */
public final class RpcClientAttributesExtractor<REQUEST, RESPONSE>
    extends RpcCommonAttributesExtractor<REQUEST, RESPONSE> implements SpanKeyProvider {

  /** Creates the RPC client attributes extractor. */
  public static <REQUEST, RESPONSE> RpcClientAttributesExtractor<REQUEST, RESPONSE> create(
      RpcAttributesGetter<REQUEST> getter) {
    return new RpcClientAttributesExtractor<>(getter);
  }

  private RpcClientAttributesExtractor(RpcAttributesGetter<REQUEST> getter) {
    super(getter);
  }

  /**
   * This method is internal and is hence not for public use. Its API is unstable and can change at
   * any time.
   */
  @UnstableApi
  @Override
  public SpanKey internalGetSpanKey() {
    return SpanKey.RPC_CLIENT;
  }
}
