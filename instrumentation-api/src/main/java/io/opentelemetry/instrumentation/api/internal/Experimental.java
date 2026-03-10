/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Collections.emptySet;

import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractorBuilder;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class Experimental {

  @Nullable
  private static volatile BiConsumer<HttpClientAttributesExtractorBuilder<?, ?>, Set<String>>
      clientSensitiveQueryParameters;

  @Nullable
  private static volatile BiConsumer<HttpServerAttributesExtractorBuilder<?, ?>, Set<String>>
      serverSensitiveQueryParameters;

  @Nullable
  private static volatile BiConsumer<HttpSpanNameExtractorBuilder<?>, Function<?, String>>
      urlTemplateExtractorSetter;

  @Nullable
  private static volatile BiConsumer<InstrumenterBuilder<?, ?>, AttributesExtractor<?, ?>>
      operationListenerAttributesExtractorAdder;

  private Experimental() {}

  /**
   * @deprecated Use {@link #setSensitiveQueryParameters(HttpClientAttributesExtractorBuilder, Set)}
   *     instead.
   */
  @Deprecated
  public static void setRedactQueryParameters(
      HttpClientAttributesExtractorBuilder<?, ?> builder, boolean redactQueryParameters) {
    setSensitiveQueryParameters(
        builder, redactQueryParameters ? HttpConstants.SENSITIVE_QUERY_PARAMETERS : emptySet());
  }

  public static void setSensitiveQueryParameters(
      HttpClientAttributesExtractorBuilder<?, ?> builder, Set<String> sensitiveQueryParameters) {
    if (clientSensitiveQueryParameters != null) {
      clientSensitiveQueryParameters.accept(builder, sensitiveQueryParameters);
    }
  }

  public static void setSensitiveQueryParameters(
      HttpServerAttributesExtractorBuilder<?, ?> builder, Set<String> sensitiveQueryParameters) {
    if (serverSensitiveQueryParameters != null) {
      serverSensitiveQueryParameters.accept(builder, sensitiveQueryParameters);
    }
  }

  public static void internalSetClientSensitiveQueryParameters(
      BiConsumer<HttpClientAttributesExtractorBuilder<?, ?>, Set<String>>
          clientSensitiveQueryParameters) {
    Experimental.clientSensitiveQueryParameters = clientSensitiveQueryParameters;
  }

  public static void internalSetServerSensitiveQueryParameters(
      BiConsumer<HttpServerAttributesExtractorBuilder<?, ?>, Set<String>>
          serverSensitiveQueryParameters) {
    Experimental.serverSensitiveQueryParameters = serverSensitiveQueryParameters;
  }

  public static <REQUEST> void setUrlTemplateExtractor(
      HttpSpanNameExtractorBuilder<REQUEST> builder,
      Function<REQUEST, String> urlTemplateExtractor) {
    if (urlTemplateExtractorSetter != null) {
      urlTemplateExtractorSetter.accept(builder, urlTemplateExtractor);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"}) // we lose the generic type information
  public static <REQUEST> void internalSetUrlTemplateExtractor(
      BiConsumer<HttpSpanNameExtractorBuilder<REQUEST>, Function<REQUEST, String>>
          urlTemplateExtractorSetter) {
    Experimental.urlTemplateExtractorSetter = (BiConsumer) urlTemplateExtractorSetter;
  }

  /**
   * Add an {@link AttributesExtractor} to the given {@link InstrumenterBuilder} that provides
   * attributes that are passed to the {@link OperationListener}s. This can be used to add
   * attributes to the metrics without adding them to the span. To add attributes to the span use
   * {@link InstrumenterBuilder#addAttributesExtractor(AttributesExtractor)}.
   */
  public static <REQUEST, RESPONSE> void addOperationListenerAttributesExtractor(
      InstrumenterBuilder<REQUEST, RESPONSE> builder,
      AttributesExtractor<? super REQUEST, ? super RESPONSE> attributesExtractor) {
    if (operationListenerAttributesExtractorAdder != null) {
      operationListenerAttributesExtractorAdder.accept(builder, attributesExtractor);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"}) // we lose the generic type information
  public static <REQUEST, RESPONSE> void internalAddOperationListenerAttributesExtractor(
      BiConsumer<
              InstrumenterBuilder<REQUEST, RESPONSE>,
              AttributesExtractor<? super REQUEST, ? super RESPONSE>>
          operationListenerAttributesExtractorAdder) {
    Experimental.operationListenerAttributesExtractorAdder =
        (BiConsumer) operationListenerAttributesExtractorAdder;
  }
}
