/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import javax.annotation.Nullable;

/**
 * A type adapter for client-based network attributes. It adapts from a type-specific request and
 * response into the 4 common network attribute values.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the NetClientAttributesExtractor to obtain the various
 * network attributes in a type-generic way.
 */
public interface NetClientAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  String transport(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  String peerName(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  Integer peerPort(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  String peerIp(REQUEST request, @Nullable RESPONSE response);
}
