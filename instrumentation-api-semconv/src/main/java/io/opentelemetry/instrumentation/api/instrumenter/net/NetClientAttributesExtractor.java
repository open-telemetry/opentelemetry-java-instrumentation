/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.InternalNetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.AddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalNetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.ServerAddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#server-client-and-shared-network-attributes">Network
 * attributes</a>.
 *
 * <p>This class delegates to a type-specific {@link NetClientAttributesGetter} for individual
 * attribute extraction from request/response objects.
 *
 * @deprecated Make sure that your instrumentation uses the extractors from the {@code ...network}
 *     package instead. This class will be removed in the 2.0 release.
 */
@Deprecated
public final class NetClientAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final InternalNetClientAttributesExtractor<REQUEST, RESPONSE> internalExtractor;
  private final InternalNetworkAttributesExtractor<REQUEST, RESPONSE> internalNetworkExtractor;
  private final InternalServerAttributesExtractor<REQUEST> internalServerExtractor;

  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      NetClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return new NetClientAttributesExtractor<>(getter);
  }

  private NetClientAttributesExtractor(NetClientAttributesGetter<REQUEST, RESPONSE> getter) {
    ServerAddressAndPortExtractor<REQUEST> serverAddressAndPortExtractor =
        new ServerAddressAndPortExtractor<>(getter, AddressAndPortExtractor.noop());

    internalExtractor =
        new InternalNetClientAttributesExtractor<>(
            getter, AddressAndPortExtractor.noop(), SemconvStability.emitOldHttpSemconv());
    internalNetworkExtractor =
        new InternalNetworkAttributesExtractor<>(
            getter,
            AddressAndPortExtractor.noop(),
            serverAddressAndPortExtractor,
            /* captureNetworkTransportAndType= */ true,
            /* captureLocalSocketAttributes= */ false,
            /* captureOldPeerDomainAttribute= */ true,
            SemconvStability.emitStableHttpSemconv(),
            SemconvStability.emitOldHttpSemconv());
    internalServerExtractor =
        new InternalServerAttributesExtractor<>(
            (port, request) -> true,
            serverAddressAndPortExtractor,
            SemconvStability.emitStableHttpSemconv(),
            SemconvStability.emitOldHttpSemconv(),
            InternalServerAttributesExtractor.Mode.PEER);
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalServerExtractor.onStart(attributes, request);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    internalExtractor.onEnd(attributes, request, response);
    internalNetworkExtractor.onEnd(attributes, request, response);
  }
}
