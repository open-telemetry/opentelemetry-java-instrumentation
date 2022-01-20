/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import javax.annotation.Nullable;

/**
 * An interface for getting server-based network attributes. It adapts a vendor-specific request
 * type into the 4 common attributes (transport, peerName, peerPort, peerIp).
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * server library/framework. It will be used by the NetServerAttributesExtractor to obtain the
 * various network attributes in a type-generic way.
 */
public interface NetServerAttributesGetter<REQUEST> {

  @Nullable
  String transport(REQUEST request);

  @Nullable
  String peerName(REQUEST request);

  @Nullable
  Integer peerPort(REQUEST request);

  @Nullable
  String peerIp(REQUEST request);
}
