/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.netty.util.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

public class AttributeKeys {

  public static final AttributeKey<Context> CONNECT_CONTEXT_ATTRIBUTE_KEY =
      AttributeKey.valueOf(AttributeKeys.class, "connect.context");

  // this attribute key is also used by ratpack instrumentation
  public static final AttributeKey<Context> SERVER_ATTRIBUTE_KEY =
      AttributeKey.valueOf(AttributeKeys.class.getName() + ".context");

  public static final AttributeKey<Span> CLIENT_ATTRIBUTE_KEY =
      AttributeKey.valueOf(AttributeKeys.class.getName() + ".span");

  public static final AttributeKey<Context> CLIENT_PARENT_ATTRIBUTE_KEY =
      AttributeKey.valueOf(AttributeKeys.class.getName() + ".parent");
}
