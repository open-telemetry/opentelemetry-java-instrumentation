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
  String getTransport(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  String getPeerName(REQUEST request);

  @Nullable
  Integer getPeerPort(REQUEST request);

  @Nullable
  default String getSockFamily(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  @Nullable
  default String getSockPeerAddr(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  @Nullable
  default String getSockPeerName(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  @Nullable
  default Integer getSockPeerPort(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }
}
