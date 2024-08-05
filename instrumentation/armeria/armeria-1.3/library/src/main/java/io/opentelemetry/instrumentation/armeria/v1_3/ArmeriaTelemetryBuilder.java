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
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.armeria.v1_3.internal.ArmeriaInstrumenterBuilderFactory;
import io.opentelemetry.instrumentation.armeria.v1_3.internal.ArmeriaInstrumenterBuilderUtil;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class ArmeriaTelemetryBuilder {

  private final DefaultHttpClientInstrumenterBuilder<ClientRequestContext, RequestLog>
      clientBuilder;
  private final DefaultHttpServerInstrumenterBuilder<ServiceRequestContext, RequestLog>
      serverBuilder;

  static {
    ArmeriaInstrumenterBuilderUtil.setClientBuilderExtractor(
        ArmeriaTelemetryBuilder::getClientBuilder);
    ArmeriaInstrumenterBuilderUtil.setServerBuilderExtractor(
        ArmeriaTelemetryBuilder::getServerBuilder);
  }

  ArmeriaTelemetryBuilder(OpenTelemetry openTelemetry) {
    clientBuilder = ArmeriaInstrumenterBuilderFactory.getClientBuilder(openTelemetry);
    serverBuilder = ArmeriaInstrumenterBuilderFactory.getServerBuilder(openTelemetry);
  }

  /**
   * Sets the status extractor for both client and server spans.
   *
   * @deprecated Use {@link #setClientStatusExtractor(Function)} or {@link
   *     #setServerStatusExtractor(Function)} instead.
   */
  @Deprecated
  @SuppressWarnings({"unchecked", "rawtypes"})
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setStatusExtractor(
      Function<
              SpanStatusExtractor<RequestContext, RequestLog>,
              ? extends SpanStatusExtractor<? super RequestContext, ? super RequestLog>>
          statusExtractor) {
    clientBuilder.setStatusExtractor((Function) statusExtractor);
    serverBuilder.setStatusExtractor((Function) statusExtractor);
    return this;
  }

  /** Sets the status extractor for client spans. */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setClientStatusExtractor(
      Function<
              SpanStatusExtractor<? super ClientRequestContext, ? super RequestLog>,
              ? extends SpanStatusExtractor<? super ClientRequestContext, ? super RequestLog>>
          statusExtractor) {
    clientBuilder.setStatusExtractor(statusExtractor);
    return this;
  }

  /** Sets the status extractor for server spans. */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setServerStatusExtractor(
      Function<
              SpanStatusExtractor<? super ServiceRequestContext, ? super RequestLog>,
              ? extends SpanStatusExtractor<? super ServiceRequestContext, ? super RequestLog>>
          statusExtractor) {
    serverBuilder.setStatusExtractor(statusExtractor);
    return this;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   *
   * @deprecated Use {@link #addClientAttributeExtractor(AttributesExtractor)} or {@link
   *     #addServerAttributeExtractor(AttributesExtractor)} instead.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super RequestContext, ? super RequestLog> attributesExtractor) {
    clientBuilder.addAttributeExtractor(attributesExtractor);
    serverBuilder.addAttributesExtractor(attributesExtractor);
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
    clientBuilder.addAttributeExtractor(attributesExtractor);
    return this;
  }

  /**
   * Adds an extra server-only {@link AttributesExtractor} to invoke to set attributes to
   * instrumented items. The {@link AttributesExtractor} will be executed after all default
   * extractors.
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder addServerAttributeExtractor(
      AttributesExtractor<? super ServiceRequestContext, ? super RequestLog> attributesExtractor) {
    serverBuilder.addAttributesExtractor(attributesExtractor);
    return this;
  }

  /** Sets the {@code peer.service} attribute for http client spans. */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setPeerService(String peerService) {
    clientBuilder.setPeerService(peerService);
    return this;
  }

  /**
   * Configures the HTTP client request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setCapturedClientRequestHeaders(List<String> requestHeaders) {
    clientBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP client response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setCapturedClientResponseHeaders(List<String> responseHeaders) {
    clientBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Configures the HTTP server request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setCapturedServerRequestHeaders(List<String> requestHeaders) {
    serverBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP server response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setCapturedServerResponseHeaders(List<String> responseHeaders) {
    serverBuilder.setCapturedResponseHeaders(responseHeaders);
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
    clientBuilder.setKnownMethods(knownMethods);
    serverBuilder.setKnownMethods(knownMethods);
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
    clientBuilder.setEmitExperimentalHttpClientMetrics(emitExperimentalHttpClientMetrics);
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
    serverBuilder.setEmitExperimentalHttpServerMetrics(emitExperimentalHttpServerMetrics);
    return this;
  }

  /** Sets custom client {@link SpanNameExtractor} via transform function. */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setClientSpanNameExtractor(
      Function<
              SpanNameExtractor<? super ClientRequestContext>,
              ? extends SpanNameExtractor<? super ClientRequestContext>>
          clientSpanNameExtractor) {
    clientBuilder.setSpanNameExtractor(clientSpanNameExtractor);
    return this;
  }

  /** Sets custom server {@link SpanNameExtractor} via transform function. */
  @CanIgnoreReturnValue
  public ArmeriaTelemetryBuilder setServerSpanNameExtractor(
      Function<
              SpanNameExtractor<? super ServiceRequestContext>,
              ? extends SpanNameExtractor<? super ServiceRequestContext>>
          serverSpanNameExtractor) {
    serverBuilder.setSpanNameExtractor(serverSpanNameExtractor);
    return this;
  }

  public ArmeriaTelemetry build() {
    return new ArmeriaTelemetry(clientBuilder.build(), serverBuilder.build());
  }

  private DefaultHttpClientInstrumenterBuilder<ClientRequestContext, RequestLog>
      getClientBuilder() {
    return clientBuilder;
  }

  private DefaultHttpServerInstrumenterBuilder<ServiceRequestContext, RequestLog>
      getServerBuilder() {
    return serverBuilder;
  }
}
