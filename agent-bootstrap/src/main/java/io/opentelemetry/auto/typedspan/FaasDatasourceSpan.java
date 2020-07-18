/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.typedspan;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import java.util.logging.Logger;

/**
 * <b>Required attributes:</b>
 *
 * <ul>
 *   <li>faas.trigger: Type of the trigger on which the function is executed.
 *   <li>faas.document.collection: The name of the source on which the triggering operation was
 *       performed.
 *   <li>faas.document.operation: Describes the type of the operation that was performed on the
 *       data.
 *   <li>faas.document.time: A string containing the time when the data was accessed in the [ISO
 *       8601](https://www.iso.org/iso-8601-date-and-time-format.html) format expressed in
 *       [UTC](https://www.w3.org/TR/NOTE-datetime).
 * </ul>
 *
 * <b>Conditional attributes:</b>
 *
 * <ul>
 * </ul>
 */
public class FaasDatasourceSpan extends DelegatingSpan implements FaasDatasourceSemanticConvention {

  enum AttributeStatus {
    EMPTY,
    FAAS_TRIGGER,
    FAAS_EXECUTION,
    FAAS_DOCUMENT_COLLECTION,
    FAAS_DOCUMENT_OPERATION,
    FAAS_DOCUMENT_TIME,
    FAAS_DOCUMENT_NAME;

    @SuppressWarnings("ImmutableEnumChecker")
    private long flag;

    AttributeStatus() {
      this.flag = 1L << this.ordinal();
    }

    public boolean isSet(AttributeStatus attribute) {
      return (this.flag & attribute.flag) > 0;
    }

    public void set(AttributeStatus attribute) {
      this.flag |= attribute.flag;
    }

    public void set(long attribute) {
      this.flag = attribute;
    }

    public long getValue() {
      return flag;
    }
  }

  @SuppressWarnings("unused")
  private static final Logger logger = Logger.getLogger(FaasDatasourceSpan.class.getName());

  public final AttributeStatus status;

  protected FaasDatasourceSpan(Span span, AttributeStatus status) {
    super(span);
    this.status = status;
  }

  /**
   * Entry point to generate a {@link FaasDatasourceSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link FaasDatasourceSpan} object.
   */
  public static FaasDatasourceSpanBuilder createFaasDatasourceSpanBuilder(
      Tracer tracer, String spanName) {
    return new FaasDatasourceSpanBuilder(tracer, spanName);
  }

  /**
   * Creates a {@link FaasDatasourceSpan} from a {@link FaasSpan}.
   *
   * @param builder {@link FaasSpan.FaasSpanBuilder} to use.
   * @return a {@link FaasDatasourceSpan} object built from a {@link FaasSpan}.
   */
  public static FaasDatasourceSpanBuilder createFaasDatasourceSpanBuilder(
      FaasSpan.FaasSpanBuilder builder) {
    // we accept a builder from Faas since FaasDatasource "extends" Faas
    return new FaasDatasourceSpanBuilder(builder.getSpanBuilder(), builder.status.getValue());
  }

  /** @return the Span used internally */
  @Override
  public Span getSpan() {
    return this.delegate;
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  @Override
  @SuppressWarnings("UnnecessaryParentheses")
  public void end() {
    delegate.end();

    // required attributes
    if (!this.status.isSet(AttributeStatus.FAAS_TRIGGER)) {
      logger.warning("Wrong usage - Span missing faas.trigger attribute");
    }
    if (!this.status.isSet(AttributeStatus.FAAS_DOCUMENT_COLLECTION)) {
      logger.warning("Wrong usage - Span missing faas.document.collection attribute");
    }
    if (!this.status.isSet(AttributeStatus.FAAS_DOCUMENT_OPERATION)) {
      logger.warning("Wrong usage - Span missing faas.document.operation attribute");
    }
    if (!this.status.isSet(AttributeStatus.FAAS_DOCUMENT_TIME)) {
      logger.warning("Wrong usage - Span missing faas.document.time attribute");
    }
    // extra constraints.
    // conditional attributes
  }

  /**
   * Sets faas.trigger.
   *
   * @param faasTrigger Type of the trigger on which the function is executed.
   */
  @Override
  public FaasDatasourceSemanticConvention setFaasTrigger(String faasTrigger) {
    status.set(AttributeStatus.FAAS_TRIGGER);
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
    status.set(AttributeStatus.FAAS_EXECUTION);
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
    status.set(AttributeStatus.FAAS_DOCUMENT_COLLECTION);
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
    status.set(AttributeStatus.FAAS_DOCUMENT_OPERATION);
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
    status.set(AttributeStatus.FAAS_DOCUMENT_TIME);
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
    status.set(AttributeStatus.FAAS_DOCUMENT_NAME);
    delegate.setAttribute("faas.document.name", faasDocumentName);
    return this;
  }

  /** Builder class for {@link FaasDatasourceSpan}. */
  public static class FaasDatasourceSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;
    protected AttributeStatus status = AttributeStatus.EMPTY;

    protected FaasDatasourceSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public FaasDatasourceSpanBuilder(Span.Builder spanBuilder, long attributes) {
      this.internalBuilder = spanBuilder;
      this.status.set(attributes);
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public FaasDatasourceSpanBuilder setParent(Span parent) {
      this.internalBuilder.setParent(parent);
      return this;
    }

    /** sets the {@link Span} parent. */
    public FaasDatasourceSpanBuilder setParent(SpanContext remoteParent) {
      this.internalBuilder.setParent(remoteParent);
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
      return new FaasDatasourceSpan(this.internalBuilder.startSpan(), status);
    }

    /**
     * Sets faas.trigger.
     *
     * @param faasTrigger Type of the trigger on which the function is executed.
     */
    public FaasDatasourceSpanBuilder setFaasTrigger(String faasTrigger) {
      status.set(AttributeStatus.FAAS_TRIGGER);
      internalBuilder.setAttribute("faas.trigger", faasTrigger);
      return this;
    }

    /**
     * Sets faas.execution.
     *
     * @param faasExecution The execution id of the current function execution.
     */
    public FaasDatasourceSpanBuilder setFaasExecution(String faasExecution) {
      status.set(AttributeStatus.FAAS_EXECUTION);
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
      status.set(AttributeStatus.FAAS_DOCUMENT_COLLECTION);
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
      status.set(AttributeStatus.FAAS_DOCUMENT_OPERATION);
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
      status.set(AttributeStatus.FAAS_DOCUMENT_TIME);
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
      status.set(AttributeStatus.FAAS_DOCUMENT_NAME);
      internalBuilder.setAttribute("faas.document.name", faasDocumentName);
      return this;
    }
  }
}
