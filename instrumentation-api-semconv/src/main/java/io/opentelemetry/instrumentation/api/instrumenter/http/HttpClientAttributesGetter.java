/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import javax.annotation.Nullable;

/**
 * An interface for getting HTTP client attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link HttpClientAttributesExtractor} to obtain the
 * various HTTP client attributes in a type-generic way.
 */
public interface HttpClientAttributesGetter<REQUEST, RESPONSE>
    extends HttpCommonAttributesGetter<REQUEST, RESPONSE>,
        NetClientAttributesGetter<REQUEST, RESPONSE>,
        NetworkAttributesGetter<REQUEST, RESPONSE>,
        ServerAttributesGetter<REQUEST, RESPONSE> {

  /**
   * Returns the absolute URL describing a network resource according to <a
   * href="https://www.rfc-editor.org/rfc/rfc3986">RFC3986</a>.
   *
   * <p>Examples: {@code https://www.foo.bar/search?q=OpenTelemetry#SemConv}; {@code //localhost}
   */
  @Nullable
  String getUrlFull(REQUEST request);

  /** {@inheritDoc} */
  @Nullable
  @Override
  String getServerAddress(REQUEST request);

  /** {@inheritDoc} */
  @Nullable
  @Override
  Integer getServerPort(REQUEST request);
}
