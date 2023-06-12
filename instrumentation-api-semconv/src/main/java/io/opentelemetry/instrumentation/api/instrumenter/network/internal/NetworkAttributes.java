/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NetworkAttributes {

  // FIXME: remove this class and replace its usages with SemanticAttributes once schema 1.20 is
  // released

  public static final AttributeKey<String> NETWORK_TRANSPORT = stringKey("network.transport");

  public static final AttributeKey<String> NETWORK_TYPE = stringKey("network.type");

  public static final AttributeKey<String> NETWORK_PROTOCOL_NAME =
      stringKey("network.protocol.name");

  public static final AttributeKey<String> NETWORK_PROTOCOL_VERSION =
      stringKey("network.protocol.version");

  private NetworkAttributes() {}
}
