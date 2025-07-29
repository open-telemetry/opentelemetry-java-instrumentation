/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal;

import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AttributeKeys {

  // this is the context that has the server span
  //
  // note: this attribute key is also used by finagle instrumentation
  public static final AttributeKey<ServerContexts> SERVER_CONTEXTS =
      AttributeKey.valueOf(AttributeKeys.class, "server-contexts");

  public static final AttributeKey<Context> CLIENT_CONTEXT =
      AttributeKey.valueOf(AttributeKeys.class, "client-context");

  public static final AttributeKey<Context> CLIENT_PARENT_CONTEXT =
      AttributeKey.valueOf(AttributeKeys.class, "client-parent-context");

  private AttributeKeys() {}
}
