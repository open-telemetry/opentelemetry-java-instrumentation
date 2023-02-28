/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

final class WebClientExperimentalAttributesExtractor
    implements AttributesExtractor<ClientRequest, ClientResponse> {

  private static final AttributeKey<String> SPRING_WEBFLUX_EVENT =
      stringKey("spring-webflux.event");
  private static final AttributeKey<String> SPRING_WEBFLUX_MESSAGE =
      stringKey("spring-webflux.message");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, ClientRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ClientRequest request,
      @Nullable ClientResponse response,
      @Nullable Throwable error) {

    // no response and no error means that the request has been cancelled
    if (response == null && error == null) {
      attributes.put(SPRING_WEBFLUX_EVENT, "cancelled");
      attributes.put(SPRING_WEBFLUX_MESSAGE, "The subscription was cancelled");
    }
  }
}
