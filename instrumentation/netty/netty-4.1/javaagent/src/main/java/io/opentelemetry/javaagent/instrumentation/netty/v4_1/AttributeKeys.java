/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;

public class AttributeKeys {

  public static final AttributeKey<Context> CONNECT_CONTEXT =
      AttributeKey.valueOf(AttributeKeys.class, "connect-context");

  // this is the context that has the server span
  //
  // note: this attribute key is also used by ratpack instrumentation
  public static final AttributeKey<Context> SERVER_SPAN =
      AttributeKey.valueOf(AttributeKeys.class, "server-span");

  public static final AttributeKey<HttpClientOperation<HttpResponse>> CLIENT_OPERATION =
      AttributeKey.valueOf(AttributeKeys.class, "client-operation");
}
