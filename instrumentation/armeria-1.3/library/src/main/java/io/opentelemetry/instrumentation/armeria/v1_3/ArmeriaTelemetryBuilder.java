/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRouteBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.armeria.v1_3.internal.ArmeriaHttpClientAttributesGetter;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public final class ArmeriaTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.armeria-1.3";

  private final OpenTelemetry openTelemetry;
  @Nullable private String peerService;
  private boolean emitExperimentalHttpClientMetrics = false;
  private boolean emitExperimentalHttpServerMetrics = false;

  private final List<AttributesExtractor<? super RequestContext, ? super RequestLog>>
      additionalExtractors = new ArrayList<>();
  private final List<AttributesExtractor<? super ClientRequestContext, ? super RequestLog>>
      additionalClientExtractors = new ArrayList<>();

  private final HttpClientAttributesExtractorBuilder<RequestContext, RequestLog>
      httpClientAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(ArmeriaHttpClientAttributesGetter.INSTANCE);
  private final HttpServerAttributesExtractorBuilder<RequestContext, RequestLog>
      httpServerAttributesExtractorBuilder =
          HttpServerAttributesExtractor.builder(ArmeriaHttpServerAttributesGetter.INSTANCE);

  private final HttpSpanNameExtractorBuilder<RequestContext> httpClientSpanNameExtractorBuilder =
      HttpSpanNameExtractor.builder(ArmeriaHttpClientAttributesGetter.INSTANCE);
  private final HttpSpanNameExtractorBuilder<RequestContext> httpServerSpanNameExtractorBuilder =
      HttpSpanNameExtractor.builder(ArmeriaHttpServerAttributesGetter.INSTANCE);

  private final HttpServerRouteBuilder<RequestContext> httpServerRouteBuilder =
      HttpServerRoute.builder(ArmeriaHttpServerAttributesGetter.INSTANCE);

  private Function<
          SpanStatusExtractor<RequestContext, RequestLog>,
          ? extends SpanStatusExtractor<? super RequestContext, ? super RequestLog>>
      statusExtractorTransformer = Function.identity();

  ArmeriaTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setStatusExtractor(
      Function<
              SpanStatusExtractor<RequestContext, RequestLog>,
              ? extends SpanStatusExtractor<? super RequestContext, ? super RequestLog>>
          statusExtractor) {
    this.statusExtractorTransformer = statusExtractor;
    return this;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super RequestContext, ? super RequestLog> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Adds an extra client-only {@link AttributesExtractor} to invoke to set attributes to
   * instrumented items. The {@link AttributesExtractor} will be executed after all default
   * extractors.
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder addClientAttributeExtractor(
      AttributesExtractor<? super ClientRequestContext, ? super RequestLog> attributesExtractor) {
    additionalClientExtractors.add(attributesExtractor);
    return this;
  }

  /** Sets the {@code peer.service} attribute for http client spans. */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setPeerService(String peerService) {
    this.peerService = peerService;
    return this;
  }

  /**
   * Configures the HTTP client request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setCapturedClientRequestHeaders(List<String> requestHeaders) {
    httpClientAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP client response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setCapturedClientResponseHeaders(List<String> responseHeaders) {
    httpClientAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Configures the HTTP server request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setCapturedServerRequestHeaders(List<String> requestHeaders) {
    httpServerAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP server response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setCapturedServerResponseHeaders(List<String> responseHeaders) {
    httpServerAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Configures the instrumentation to recognize an alternative set of HTTP request methods.
   *
   * <p>By default, this instrumentation defines "known" methods as the ones listed in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and the PATCH
   * method defined in <a href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>.
   *
   * <p>Note: calling this method <b>overrides</b> the default known method sets completely; it does
   * not supplement it.
   *
   * @param knownMethods A set of recognized HTTP request methods.
   * @see HttpClientAttributesExtractorBuilder#setKnownMethods(Set)
   * @see HttpServerAttributesExtractorBuilder#setKnownMethods(Set)
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    httpClientAttributesExtractorBuilder.setKnownMethods(knownMethods);
    httpServerAttributesExtractorBuilder.setKnownMethods(knownMethods);
    httpClientSpanNameExtractorBuilder.setKnownMethods(knownMethods);
    httpServerSpanNameExtractorBuilder.setKnownMethods(knownMethods);
    httpServerRouteBuilder.setKnownMethods(knownMethods);
    return this;
  }

  /**
   * Configures the instrumentation to emit experimental HTTP client metrics.
   *
   * @param emitExperimentalHttpClientMetrics {@code true} if the experimental HTTP client metrics
   *     are to be emitted.
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setEmitExperimentalHttpClientMetrics(
      boolean emitExperimentalHttpClientMetrics) {
    this.emitExperimentalHttpClientMetrics = emitExperimentalHttpClientMetrics;
    return this;
  }

  /**
   * Configures the instrumentation to emit experimental HTTP server metrics.
   *
   * @param emitExperimentalHttpServerMetrics {@code true} if the experimental HTTP server metrics
   *     are to be emitted.
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setEmitExperimentalHttpServerMetrics(
      boolean emitExperimentalHttpServerMetrics) {
    this.emitExperimentalHttpServerMetrics = emitExperimentalHttpServerMetrics;
    return this;
  }

  public ArmeriaTelemetry build() {
    ArmeriaHttpClientAttributesGetter clientAttributesGetter =
        ArmeriaHttpClientAttributesGetter.INSTANCE;
    ArmeriaHttpServerAttributesGetter serverAttributesGetter =
        ArmeriaHttpServerAttributesGetter.INSTANCE;

    InstrumenterBuilder<ClientRequestContext, RequestLog> clientInstrumenterBuilder =
        Instrumenter.builder(
            openTelemetry, INSTRUMENTATION_NAME, httpClientSpanNameExtractorBuilder.build());
    InstrumenterBuilder<ServiceRequestContext, RequestLog> serverInstrumenterBuilder =
        Instrumenter.builder(
            openTelemetry, INSTRUMENTATION_NAME, httpServerSpanNameExtractorBuilder.build());

    Stream.of(clientInstrumenterBuilder, serverInstrumenterBuilder)
        .forEach(instrumenter -> instrumenter.addAttributesExtractors(additionalExtractors));

    clientInstrumenterBuilder
        .setSpanStatusExtractor(
            statusExtractorTransformer.apply(
                HttpSpanStatusExtractor.create(clientAttributesGetter)))
        .addAttributesExtractor(httpClientAttributesExtractorBuilder.build())
        .addAttributesExtractors(additionalClientExtractors)
        .addOperationMetrics(HttpClientMetrics.get());
    serverInstrumenterBuilder
        .setSpanStatusExtractor(
            statusExtractorTransformer.apply(
                HttpSpanStatusExtractor.create(serverAttributesGetter)))
        .addAttributesExtractor(httpServerAttributesExtractorBuilder.build())
        .addOperationMetrics(HttpServerMetrics.get())
        .addContextCustomizer(httpServerRouteBuilder.build());

    if (peerService != null) {
      clientInstrumenterBuilder.addAttributesExtractor(
          AttributesExtractor.constant(SemanticAttributes.PEER_SERVICE, peerService));
    }
    if (emitExperimentalHttpClientMetrics) {
      clientInstrumenterBuilder.addOperationMetrics(HttpClientExperimentalMetrics.get());
    }
    if (emitExperimentalHttpServerMetrics) {
      serverInstrumenterBuilder.addOperationMetrics(HttpServerExperimentalMetrics.get());
    }

    return new ArmeriaTelemetry(
        clientInstrumenterBuilder.buildClientInstrumenter(ClientRequestContextSetter.INSTANCE),
        serverInstrumenterBuilder.buildServerInstrumenter(RequestContextGetter.INSTANCE));
  }
}
