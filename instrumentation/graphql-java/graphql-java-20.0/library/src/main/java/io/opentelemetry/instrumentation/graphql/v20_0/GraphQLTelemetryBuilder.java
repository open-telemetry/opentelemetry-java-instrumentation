/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;

/** A builder of {@link GraphQLTelemetry}. */
@SuppressWarnings({"AbbreviationAsWordInName", "MemberName"})
public final class GraphQLTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean captureQuery = true;
  private boolean sanitizeQuery = true;
  private boolean dataFetcherInstrumentationEnabled = false;
  private boolean trivialDataFetcherInstrumentationEnabled = false;
  private boolean addOperationNameToSpanName = false;
  private boolean operationSpanEnabled = true;
  private boolean addAttributesToCurrentSpan = false;

  GraphQLTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether query should be captured in {@code graphql.document} span attribute. Default is
   * {@code true}.
   */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setCaptureQuery(boolean captureQuery) {
    this.captureQuery = captureQuery;
    return this;
  }

  /** Sets whether sensitive information should be removed from queries. Default is {@code true}. */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setQuerySanitizationEnabled(boolean sanitizeQuery) {
    this.sanitizeQuery = sanitizeQuery;
    return this;
  }

  /**
   * @deprecated Use {@link #setQuerySanitizationEnabled(boolean)} instead. Will be removed in 3.0.
   */
  @Deprecated // to be removed in 3.0
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setSanitizeQuery(boolean sanitizeQuery) {
    return setQuerySanitizationEnabled(sanitizeQuery);
  }

  /** Sets whether spans are created for GraphQL Data Fetchers. Default is {@code false}. */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setDataFetcherInstrumentationEnabled(
      boolean dataFetcherInstrumentationEnabled) {
    this.dataFetcherInstrumentationEnabled = dataFetcherInstrumentationEnabled;
    return this;
  }

  /**
   * Sets whether spans are created for trivial GraphQL Data Fetchers. A trivial DataFetcher is one
   * that simply maps data from an object to a field. Default is {@code false}.
   */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setTrivialDataFetcherInstrumentationEnabled(
      boolean trivialDataFetcherInstrumentationEnabled) {
    this.trivialDataFetcherInstrumentationEnabled = trivialDataFetcherInstrumentationEnabled;
    return this;
  }

  /**
   * Sets whether GraphQL operation name is added to the span name. Default is {@code false}.
   *
   * <p>WARNING: GraphQL operation name is provided by the client and can have high cardinality. Use
   * only when the server is not exposed to malicious clients.
   */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setOperationNameInSpanNameEnabled(
      boolean addOperationNameToSpanName) {
    this.addOperationNameToSpanName = addOperationNameToSpanName;
    return this;
  }

  /**
   * @deprecated Use {@link #setOperationNameInSpanNameEnabled(boolean)} instead. Will be removed in
   *     3.0.
   */
  @Deprecated // to be removed in 3.0
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setAddOperationNameToSpanName(boolean addOperationNameToSpanName) {
    return setOperationNameInSpanNameEnabled(addOperationNameToSpanName);
  }

  /**
   * Sets whether the GraphQL operation span is created. Default is {@code true}.
   *
   * <p>When disabled, no {@code GraphQL Operation} span is created; spans for data fetchers, if
   * enabled, are unaffected and continue to nest under the current span. If disabled while {@link
   * #setAddAttributesToCurrentSpan(boolean)} is enabled but there is no valid current span to stamp
   * onto, the operation span is created anyway so that telemetry is not lost.
   */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setOperationSpanEnabled(boolean operationSpanEnabled) {
    this.operationSpanEnabled = operationSpanEnabled;
    return this;
  }

  /**
   * Sets whether GraphQL attributes ({@code graphql.operation.name}, {@code graphql.operation.type}
   * and, when {@link #setCaptureQuery(boolean) enabled}, {@code graphql.document}) and exception
   * events are added to the current span. Default is {@code false}.
   *
   * <p>The current span is the span that is active when GraphQL execution begins, which in an HTTP
   * context is typically the server span (e.g. {@code POST /graphql}). This lets GraphQL telemetry
   * be recorded on that span in addition to, or instead of (see {@link
   * #setOperationSpanEnabled(boolean)}), the dedicated GraphQL operation span.
   *
   * <p>WARNING: when this is enabled and the GraphQL result contains errors, the current span's
   * status is set to {@code ERROR}. This can mark an otherwise successful (e.g. HTTP 200) server
   * span as errored, including for partial or expected GraphQL errors.
   *
   * <p>When there is no valid current span, attributes are instead recorded on a GraphQL operation
   * span (created even if {@link #setOperationSpanEnabled(boolean)} is disabled) so that telemetry
   * is not lost.
   */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setAddAttributesToCurrentSpan(boolean addAttributesToCurrentSpan) {
    this.addAttributesToCurrentSpan = addAttributesToCurrentSpan;
    return this;
  }

  /**
   * Returns a new {@link GraphQLTelemetry} with the settings of this {@link
   * GraphQLTelemetryBuilder}.
   */
  public GraphQLTelemetry build() {
    return new GraphQLTelemetry(
        openTelemetry,
        captureQuery,
        sanitizeQuery,
        GraphqlInstrumenterFactory.createDataFetcherInstrumenter(
            openTelemetry, dataFetcherInstrumentationEnabled),
        trivialDataFetcherInstrumentationEnabled,
        addOperationNameToSpanName,
        operationSpanEnabled,
        addAttributesToCurrentSpan);
  }
}
