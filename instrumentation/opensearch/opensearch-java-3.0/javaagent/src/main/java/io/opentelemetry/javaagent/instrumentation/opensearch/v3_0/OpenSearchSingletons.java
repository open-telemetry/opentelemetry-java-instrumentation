/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import org.opensearch.client.transport.OpenSearchTransport;

public class OpenSearchSingletons {
  private static final Instrumenter<OpenSearchRequest, Void> instrumenter = createInstrumenter();
  private static final VirtualField<OpenSearchTransport, OpenSearchServerAddress> SERVER_ADDRESS =
      VirtualField.find(OpenSearchTransport.class, OpenSearchServerAddress.class);
  private static final VirtualField<OpenSearchTransport, Object> REST_CLIENT =
      VirtualField.find(OpenSearchTransport.class, Object.class);

  public static final boolean CAPTURE_SEARCH_QUERY =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "opensearch")
          .getBoolean("capture_search_query", true);

  public static Instrumenter<OpenSearchRequest, Void> instrumenter() {
    return instrumenter;
  }

  static void setServerAddress(
      OpenSearchTransport transport, OpenSearchServerAddress addressAndPort) {
    SERVER_ADDRESS.set(transport, addressAndPort);
  }

  @Nullable
  static OpenSearchServerAddress serverAddress(OpenSearchTransport transport) {
    return SERVER_ADDRESS.get(transport);
  }

  public static void setRestClient(OpenSearchTransport transport, Object restClient) {
    REST_CLIENT.set(transport, restClient);
  }

  @Nullable
  static Object restClient(OpenSearchTransport transport) {
    return REST_CLIENT.get(transport);
  }

  private static Instrumenter<OpenSearchRequest, Void> createInstrumenter() {
    OpenSearchAttributesGetter dbClientAttributesGetter = new OpenSearchAttributesGetter();

    InstrumenterBuilder<OpenSearchRequest, Void> builder =
        Instrumenter.<OpenSearchRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.opensearch-java-3.0",
                DbClientSpanNameExtractor.create(dbClientAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private OpenSearchSingletons() {}
}
