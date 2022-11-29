/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import javax.annotation.Nullable;

/**
 * An interface for getting client-based network attributes. It adapts from a type-specific request
 * and response into the 4 common network attribute values.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the NetClientAttributesExtractor to obtain the various
 * network attributes in a type-generic way.
 */
public interface NetClientAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  String transport(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  String peerName(REQUEST request);

  @Nullable
  Integer peerPort(REQUEST request);

  @Nullable
  default String sockFamily(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  @Nullable
  default String sockPeerAddr(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  @Nullable
  default String sockPeerName(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  @Nullable
  default Integer sockPeerPort(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }
}
