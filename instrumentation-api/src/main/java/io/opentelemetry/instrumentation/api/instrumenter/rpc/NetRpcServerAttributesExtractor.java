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
 * href="https://github.com/open-telemetry/opentelemetry-specification/issues/2214">Network
 * attributes</a>. It's used in rpc server
 */
public abstract class NetRpcServerAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  @Override
  public final void onStart(AttributesBuilder attributes, REQUEST request) {
    set(attributes, SemanticAttributes.NET_TRANSPORT, transport(request));

    String hostIp = hostIp(request);
    String hostName = hostName(request);
    if (hostIp != null && !hostIp.equals(hostName)) {
      set(attributes, SemanticAttributes.NET_HOST_IP, hostIp);
    }
    set(attributes, SemanticAttributes.NET_HOST_NAME, hostName);
  }

  @Override
  public final void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}

  @Nullable
  public abstract String transport(REQUEST request);

  @Nullable
  public abstract String hostName(REQUEST request);

  @Nullable
  public abstract String hostIp(REQUEST request);
}
