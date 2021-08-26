/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.asynchttpclient.Response;
import org.asynchttpclient.netty.request.NettyRequest;
import org.checkerframework.checker.nullness.qual.Nullable;

public class AsyncHttpClientAdditionalAttributesExtractor
    extends AttributesExtractor<RequestContext, Response> {

  @Override
  protected void onStart(AttributesBuilder attributes, RequestContext requestContext) {}

  @Override
  protected void onEnd(
      AttributesBuilder attributes, RequestContext requestContext, @Nullable Response response) {
    NettyRequest nettyRequest = requestContext.getNettyRequest();
    if (nettyRequest != null) {
      set(
          attributes,
          SemanticAttributes.HTTP_USER_AGENT,
          nettyRequest.getHttpRequest().headers().get("User-Agent"));
    }
  }
}
