/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.InternalNetServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.FallbackAddressPortExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalNetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkTransportFilter;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#server-client-and-shared-network-attributes">Network
 * attributes</a>.
 *
 * @deprecated Make sure that your instrumentation uses the extractors from the {@code ...network}
 *     package instead. This class will be removed in the 2.0 release.
 */
@Deprecated
public final class NetServerAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      NetServerAttributesGetter<REQUEST, RESPONSE> getter) {
    return new NetServerAttributesExtractor<>(getter);
  }

  private final InternalNetServerAttributesExtractor<REQUEST, RESPONSE> internalExtractor;
  private final InternalNetworkAttributesExtractor<REQUEST, RESPONSE> internalNetworkExtractor;
  private final InternalServerAttributesExtractor<REQUEST, RESPONSE> internalServerExtractor;
  private final InternalClientAttributesExtractor<REQUEST, RESPONSE> internalClientExtractor;

  private NetServerAttributesExtractor(NetServerAttributesGetter<REQUEST, RESPONSE> getter) {
    internalExtractor =
        new InternalNetServerAttributesExtractor<>(
            getter, FallbackAddressPortExtractor.noop(), SemconvStability.emitOldHttpSemconv());
    internalNetworkExtractor =
        new InternalNetworkAttributesExtractor<>(
            getter,
            NetworkTransportFilter.alwaysTrue(),
            SemconvStability.emitStableHttpSemconv(),
            SemconvStability.emitOldHttpSemconv());
    internalServerExtractor =
        new InternalServerAttributesExtractor<>(
            getter,
            (port, request) -> true,
            FallbackAddressPortExtractor.noop(),
            SemconvStability.emitStableHttpSemconv(),
            SemconvStability.emitOldHttpSemconv(),
            InternalServerAttributesExtractor.Mode.HOST,
            /* captureServerSocketAttributes= */ true);
    internalClientExtractor =
        new InternalClientAttributesExtractor<>(
            getter,
            FallbackAddressPortExtractor.noop(),
            SemconvStability.emitStableHttpSemconv(),
            SemconvStability.emitOldHttpSemconv());
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalExtractor.onStart(attributes, request);
    internalServerExtractor.onStart(attributes, request);
    internalClientExtractor.onStart(attributes, request);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    internalNetworkExtractor.onEnd(attributes, request, response);
    internalServerExtractor.onEnd(attributes, request, response);
    internalClientExtractor.onEnd(attributes, request, response);
  }
}
