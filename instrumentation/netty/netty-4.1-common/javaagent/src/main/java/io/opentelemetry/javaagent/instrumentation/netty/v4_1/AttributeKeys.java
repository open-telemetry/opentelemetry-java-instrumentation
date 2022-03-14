/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;

public final class AttributeKeys {

  public static final AttributeKey<Context> WRITE_CONTEXT =
      AttributeKey.valueOf(AttributeKeys.class, "passed-context");

  // this is the context that has the server span
  //
  // note: this attribute key is also used by ratpack instrumentation
  public static final AttributeKey<Context> SERVER_CONTEXT =
      AttributeKey.valueOf(AttributeKeys.class, "server-context");

  public static final AttributeKey<Context> CLIENT_CONTEXT =
      AttributeKey.valueOf(AttributeKeys.class, "client-context");

  public static final AttributeKey<Context> CLIENT_PARENT_CONTEXT =
      AttributeKey.valueOf(AttributeKeys.class, "client-parent-context");

  private AttributeKeys() {}
}
