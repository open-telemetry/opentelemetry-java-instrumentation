/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.v7_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.elasticsearch.rest.internal.ElasticsearchRestInstrumenterFactory;
import io.opentelemetry.instrumentation.elasticsearch.rest.internal.ElasticsearchRestRequest;
import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.client.Response;

public final class ElasticsearchRest7TelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.elasticsearch-rest-7.0";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<ElasticsearchRestRequest, Response>> attributesExtractors =
      new ArrayList<>();

  ElasticsearchRest7TelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public ElasticsearchRest7TelemetryBuilder addAttributesExtractor(
      AttributesExtractor<ElasticsearchRestRequest, Response> attributesExtractor) {
    attributesExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Returns a new {@link ElasticsearchRest7Telemetry} with the settings of this {@link
   * ElasticsearchRest7TelemetryBuilder}.
   */
  public ElasticsearchRest7Telemetry build() {
    Instrumenter<ElasticsearchRestRequest, Response> instrumenter =
        ElasticsearchRestInstrumenterFactory.create(
            openTelemetry, INSTRUMENTATION_NAME, attributesExtractors, false);

    return new ElasticsearchRest7Telemetry(instrumenter);
  }
}
