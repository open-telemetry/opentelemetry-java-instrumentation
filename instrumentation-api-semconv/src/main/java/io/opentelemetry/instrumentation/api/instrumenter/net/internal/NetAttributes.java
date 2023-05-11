/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NetAttributes {

  // FIXME: remove this class and replace its usages with SemanticAttributes once schema 1.20 is
  // released

  public static final AttributeKey<String> NET_PROTOCOL_NAME = stringKey("net.protocol.name");

  public static final AttributeKey<String> NET_PROTOCOL_VERSION = stringKey("net.protocol.version");

  private NetAttributes() {}
}
