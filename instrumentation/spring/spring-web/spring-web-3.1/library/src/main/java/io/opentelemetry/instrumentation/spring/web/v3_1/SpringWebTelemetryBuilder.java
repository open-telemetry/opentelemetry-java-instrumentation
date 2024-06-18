/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.HttpClientConfigBuilder;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/** A builder of {@link SpringWebTelemetry}. */
public final class SpringWebTelemetryBuilder
    extends HttpClientConfigBuilder<SpringWebTelemetryBuilder, HttpRequest, ClientHttpResponse> {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-web-3.1";

  SpringWebTelemetryBuilder(OpenTelemetry openTelemetry) {
    super(instrumentationName, openTelemetry, SpringWebHttpAttributesGetter.INSTANCE);
  }

  /**
   * Returns a new {@link SpringWebTelemetry} with the settings of this {@link
   * SpringWebTelemetryBuilder}.
   */
  public SpringWebTelemetry build() {
    return new SpringWebTelemetry(
        instrumenterBuilder()
            .buildClientInstrumenter(HttpRequestSetter.INSTANCE));
  }
}
