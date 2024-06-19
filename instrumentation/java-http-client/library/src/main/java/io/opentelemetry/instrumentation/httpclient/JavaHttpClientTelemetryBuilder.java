/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.AbstractHttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.httpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientAttributesGetter;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

public final class JavaHttpClientTelemetryBuilder
    extends AbstractHttpClientTelemetryBuilder<
        JavaHttpClientTelemetryBuilder, HttpRequest, HttpResponse<?>> {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.java-http-client";

  JavaHttpClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    super(
        INSTRUMENTATION_NAME,
        openTelemetry,
        JavaHttpClientAttributesGetter.INSTANCE,
        Optional.empty());
  }

  public JavaHttpClientTelemetry build() {
    return new JavaHttpClientTelemetry(
        instrumenter(), new HttpHeadersSetter(openTelemetry.getPropagators()));
  }
}
