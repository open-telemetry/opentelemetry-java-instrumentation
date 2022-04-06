/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.asynchttpclient.Response;
import org.asynchttpclient.netty.request.NettyRequest;

public class AsyncHttpClientAdditionalAttributesExtractor
    implements AttributesExtractor<RequestContext, Response> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, RequestContext requestContext) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      RequestContext requestContext,
      @Nullable Response response,
      @Nullable Throwable error) {

    NettyRequest nettyRequest = requestContext.getNettyRequest();
    if (nettyRequest != null) {
      String userAgent = nettyRequest.getHttpRequest().headers().get("User-Agent");
      if (userAgent != null) {
        attributes.put(SemanticAttributes.HTTP_USER_AGENT, userAgent);
      }
    }
  }
}
