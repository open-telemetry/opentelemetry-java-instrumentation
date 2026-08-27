/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

class RatpackProtocolVersionAttributesExtractor
    implements AttributesExtractor<RequestSpec, HttpResponse> {

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, RequestSpec request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      RequestSpec request,
      @Nullable HttpResponse response,
      @Nullable Throwable error) {
    attributes.put(NETWORK_PROTOCOL_VERSION, RatpackHttpProtocolVersion.get(request));
    RatpackHttpProtocolVersion.clearRequest(request);
  }
}
