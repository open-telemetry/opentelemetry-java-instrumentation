/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/rpc.md">RPC
 * attributes</a>. Instrumentations of RPC libraries should extend this class, defining {@link
 * REQUEST} with the actual request type of the instrumented library. If an attribute is not
 * available in this library, it is appropriate to return {@code null} from the protected attribute
 * methods, but implement as many as possible for best compliance with the OpenTelemetry
 * specification.
 */
public abstract class RpcAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  @Override
  public final void onStart(AttributesBuilder attributes, REQUEST request) {
    set(attributes, SemanticAttributes.RPC_SYSTEM, system(request));
    set(attributes, SemanticAttributes.RPC_SERVICE, service(request));
    set(attributes, SemanticAttributes.RPC_METHOD, method(request));
  }

  @Override
  public final void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    // No response attributes
  }

  @Nullable
  protected abstract String system(REQUEST request);

  @Nullable
  protected abstract String service(REQUEST request);

  @Nullable
  protected abstract String method(REQUEST request);
}
