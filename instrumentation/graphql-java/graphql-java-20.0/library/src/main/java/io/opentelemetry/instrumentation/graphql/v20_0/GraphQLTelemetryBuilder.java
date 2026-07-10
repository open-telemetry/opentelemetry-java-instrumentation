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
  private boolean addAttributesToLocalRootSpan = false;
  private boolean promoteErrorStatusToLocalRootSpan = false;

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
   * enabled, are unaffected and continue to nest under the enclosing span. If this is disabled and
   * {@link #setAddAttributesToLocalRootSpan(boolean) local root enrichment} is also off, no
   * operation-level GraphQL telemetry is produced.
   */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setOperationSpanEnabled(boolean operationSpanEnabled) {
    this.operationSpanEnabled = operationSpanEnabled;
    return this;
  }

  /**
   * Sets whether GraphQL attributes ({@code graphql.operation.name}, {@code graphql.operation.type}
   * and, when {@link #setCaptureQuery(boolean) enabled}, {@code graphql.document}) and exception
   * events are added to the local root span. Default is {@code false}.
   *
   * <p>The local root span is the outermost span in the current process, which in an HTTP context
   * is typically the server span (e.g. {@code POST /graphql}). This lets GraphQL telemetry be
   * recorded on that span in addition to, or instead of (see {@link
   * #setOperationSpanEnabled(boolean)}), the dedicated GraphQL operation span. Has no effect when
   * there is no enclosing local root span.
   */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setAddAttributesToLocalRootSpan(
      boolean addAttributesToLocalRootSpan) {
    this.addAttributesToLocalRootSpan = addAttributesToLocalRootSpan;
    return this;
  }

  /**
   * Sets whether the local root span status is set to {@code ERROR} when the GraphQL result
   * contains any errors. Default is {@code false}.
   *
   * <p>WARNING: This marks the enclosing (e.g. server) span as errored for any GraphQL error,
   * including partial or expected errors returned on an otherwise successful (HTTP 200) response,
   * which is why it is off by default. It only ever sets the status to {@code ERROR}; it never
   * clears an existing status. Independent of {@link #setAddAttributesToLocalRootSpan(boolean)}.
   */
  @CanIgnoreReturnValue
  public GraphQLTelemetryBuilder setPromoteErrorStatusToLocalRootSpan(
      boolean promoteErrorStatusToLocalRootSpan) {
    this.promoteErrorStatusToLocalRootSpan = promoteErrorStatusToLocalRootSpan;
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
        addAttributesToLocalRootSpan,
        promoteErrorStatusToLocalRootSpan);
  }
}
