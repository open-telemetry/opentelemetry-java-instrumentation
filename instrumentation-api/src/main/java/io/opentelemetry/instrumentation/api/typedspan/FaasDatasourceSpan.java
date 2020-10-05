/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import io.grpc.Context;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class FaasDatasourceSpan extends DelegatingSpan implements FaasDatasourceSemanticConvention {

  protected FaasDatasourceSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link FaasDatasourceSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link FaasDatasourceSpan} object.
   */
  public static FaasDatasourceSpanBuilder createFaasDatasourceSpan(Tracer tracer, String spanName) {
    return new FaasDatasourceSpanBuilder(tracer, spanName);
  }

  /**
   * Creates a {@link FaasDatasourceSpan} from a {@link FaasSpanSpan}.
   *
   * @param builder {@link FaasSpanSpan.FaasSpanSpanBuilder} to use.
   * @return a {@link FaasDatasourceSpan} object built from a {@link FaasSpanSpan}.
   */
  public static FaasDatasourceSpanBuilder createFaasDatasourceSpan(
      FaasSpanSpan.FaasSpanSpanBuilder builder) {
    // we accept a builder from FaasSpan since FaasDatasource "extends" FaasSpan
    return new FaasDatasourceSpanBuilder(builder.getSpanBuilder());
  }

  /** @return the Span used internally */
  @Override
  public Span getSpan() {
    return this.delegate;
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  @Override
  public void end() {
    delegate.end();
  }

  /**
   * Sets faas.trigger.
   *
   * @param faasTrigger Type of the trigger on which the function is executed.
   */
  @Override
  public FaasDatasourceSemanticConvention setFaasTrigger(String faasTrigger) {
    delegate.setAttribute("faas.trigger", faasTrigger);
    return this;
  }

  /**
   * Sets faas.execution.
   *
   * @param faasExecution The execution id of the current function execution.
   */
  @Override
  public FaasDatasourceSemanticConvention setFaasExecution(String faasExecution) {
    delegate.setAttribute("faas.execution", faasExecution);
    return this;
  }

  /**
   * Sets faas.document.collection.
   *
   * @param faasDocumentCollection The name of the source on which the triggering operation was
   *     performed.
   *     <p>For example, in Cloud Storage or S3 corresponds to the bucket name, and in Cosmos DB to
   *     the database name.
   */
  @Override
  public FaasDatasourceSemanticConvention setFaasDocumentCollection(String faasDocumentCollection) {
    delegate.setAttribute("faas.document.collection", faasDocumentCollection);
    return this;
  }

  /**
   * Sets faas.document.operation.
   *
   * @param faasDocumentOperation Describes the type of the operation that was performed on the
   *     data.
   */
  @Override
  public FaasDatasourceSemanticConvention setFaasDocumentOperation(String faasDocumentOperation) {
    delegate.setAttribute("faas.document.operation", faasDocumentOperation);
    return this;
  }

  /**
   * Sets faas.document.time.
   *
   * @param faasDocumentTime A string containing the time when the data was accessed in the [ISO
   *     8601](https://www.iso.org/iso-8601-date-and-time-format.html) format expressed in
   *     [UTC](https://www.w3.org/TR/NOTE-datetime).
   */
  @Override
  public FaasDatasourceSemanticConvention setFaasDocumentTime(String faasDocumentTime) {
    delegate.setAttribute("faas.document.time", faasDocumentTime);
    return this;
  }

  /**
   * Sets faas.document.name.
   *
   * @param faasDocumentName The document name/table subjected to the operation.
   *     <p>For example, in Cloud Storage or S3 is the name of the file, and in Cosmos DB the table
   *     name.
   */
  @Override
  public FaasDatasourceSemanticConvention setFaasDocumentName(String faasDocumentName) {
    delegate.setAttribute("faas.document.name", faasDocumentName);
    return this;
  }

  /** Builder class for {@link FaasDatasourceSpan}. */
  public static class FaasDatasourceSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected FaasDatasourceSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public FaasDatasourceSpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public FaasDatasourceSpanBuilder setParent(Context context) {
      this.internalBuilder.setParent(context);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public FaasDatasourceSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public FaasDatasourceSpan start() {
      // check for sampling relevant field here, but there are none.
      return new FaasDatasourceSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets faas.trigger.
     *
     * @param faasTrigger Type of the trigger on which the function is executed.
     */
    public FaasDatasourceSpanBuilder setFaasTrigger(String faasTrigger) {
      internalBuilder.setAttribute("faas.trigger", faasTrigger);
      return this;
    }

    /**
     * Sets faas.execution.
     *
     * @param faasExecution The execution id of the current function execution.
     */
    public FaasDatasourceSpanBuilder setFaasExecution(String faasExecution) {
      internalBuilder.setAttribute("faas.execution", faasExecution);
      return this;
    }

    /**
     * Sets faas.document.collection.
     *
     * @param faasDocumentCollection The name of the source on which the triggering operation was
     *     performed.
     *     <p>For example, in Cloud Storage or S3 corresponds to the bucket name, and in Cosmos DB
     *     to the database name.
     */
    public FaasDatasourceSpanBuilder setFaasDocumentCollection(String faasDocumentCollection) {
      internalBuilder.setAttribute("faas.document.collection", faasDocumentCollection);
      return this;
    }

    /**
     * Sets faas.document.operation.
     *
     * @param faasDocumentOperation Describes the type of the operation that was performed on the
     *     data.
     */
    public FaasDatasourceSpanBuilder setFaasDocumentOperation(String faasDocumentOperation) {
      internalBuilder.setAttribute("faas.document.operation", faasDocumentOperation);
      return this;
    }

    /**
     * Sets faas.document.time.
     *
     * @param faasDocumentTime A string containing the time when the data was accessed in the [ISO
     *     8601](https://www.iso.org/iso-8601-date-and-time-format.html) format expressed in
     *     [UTC](https://www.w3.org/TR/NOTE-datetime).
     */
    public FaasDatasourceSpanBuilder setFaasDocumentTime(String faasDocumentTime) {
      internalBuilder.setAttribute("faas.document.time", faasDocumentTime);
      return this;
    }

    /**
     * Sets faas.document.name.
     *
     * @param faasDocumentName The document name/table subjected to the operation.
     *     <p>For example, in Cloud Storage or S3 is the name of the file, and in Cosmos DB the
     *     table name.
     */
    public FaasDatasourceSpanBuilder setFaasDocumentName(String faasDocumentName) {
      internalBuilder.setAttribute("faas.document.name", faasDocumentName);
      return this;
    }
  }
}
