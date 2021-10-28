/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client;

import io.opentelemetry.javaagent.instrumentation.vertx.client.AbstractVertxHttpAttributesExtractor;
import io.vertx.core.http.HttpClientRequest;
import javax.annotation.Nullable;

final class Vertx3HttpAttributesExtractor extends AbstractVertxHttpAttributesExtractor {

  @Nullable
  @Override
  protected String url(HttpClientRequest request) {
    return request.uri();
  }

  @Override
  protected String method(HttpClientRequest request) {
    return request.method().name();
  }
}
