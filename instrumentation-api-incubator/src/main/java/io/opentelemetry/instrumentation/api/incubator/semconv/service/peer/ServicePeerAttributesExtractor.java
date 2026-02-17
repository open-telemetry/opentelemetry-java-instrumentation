/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.service.peer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.internal.ServicePeerResolver;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import javax.annotation.Nullable;

/**
 * Extractor of the {@code service.peer.name} span attribute, described in <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#general-remote-service-attributes">the
 * specification</a>.
 */
public final class ServicePeerAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final ServerAttributesGetter<REQUEST> attributesGetter;
  private final ServicePeerResolver servicePeerResolver;

  // visible for tests
  ServicePeerAttributesExtractor(
      ServerAttributesGetter<REQUEST> attributesGetter, ServicePeerResolver servicePeerResolver) {
    this.attributesGetter = attributesGetter;
    this.servicePeerResolver = servicePeerResolver;
  }

  /**
   * Returns a new {@link ServicePeerAttributesExtractor} that will use the passed {@code
   * attributesGetter} instance to determine the value of the {@code service.peer.name} attribute.
   *
   * @param attributesGetter the getter to extract server address and port from the request
   * @param openTelemetry the OpenTelemetry instance to read service peer mapping configuration from
   */
  // TODO: replace OpenTelemetry parameter with ConfigProvider once it is stabilized and available
  // via openTelemetry.getConfigProvider()
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      ServerAttributesGetter<REQUEST> attributesGetter, OpenTelemetry openTelemetry) {
    ServicePeerResolver servicePeerResolver = new ServicePeerResolver(openTelemetry);
    if (servicePeerResolver.isEmpty()) {
      return new EmptyAttributesExtractor<>();
    }
    return new ServicePeerAttributesExtractor<>(attributesGetter, servicePeerResolver);
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    String serverAddress = attributesGetter.getServerAddress(request);
    if (serverAddress == null) {
      return;
    }

    servicePeerResolver.resolve(
        serverAddress, attributesGetter.getServerPort(request), () -> null, attributes::put);
  }

  private static final class EmptyAttributesExtractor<REQUEST, RESPONSE>
      implements AttributesExtractor<REQUEST, RESPONSE> {

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {}

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        REQUEST request,
        @Nullable RESPONSE response,
        @Nullable Throwable error) {}
  }
}
