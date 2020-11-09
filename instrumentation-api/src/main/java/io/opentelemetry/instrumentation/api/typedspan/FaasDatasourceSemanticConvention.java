/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import io.opentelemetry.api.trace.Span;

public interface FaasDatasourceSemanticConvention {
  void end();

  Span getSpan();

  /**
   * Sets a value for faas.trigger
   *
   * @param faasTrigger Type of the trigger on which the function is executed.
   */
  FaasDatasourceSemanticConvention setFaasTrigger(String faasTrigger);

  /**
   * Sets a value for faas.execution
   *
   * @param faasExecution The execution id of the current function execution.
   */
  FaasDatasourceSemanticConvention setFaasExecution(String faasExecution);

  /**
   * Sets a value for faas.document.collection
   *
   * @param faasDocumentCollection The name of the source on which the triggering operation was
   *     performed.
   *     <p>For example, in Cloud Storage or S3 corresponds to the bucket name, and in Cosmos DB to
   *     the database name.
   */
  FaasDatasourceSemanticConvention setFaasDocumentCollection(String faasDocumentCollection);

  /**
   * Sets a value for faas.document.operation
   *
   * @param faasDocumentOperation Describes the type of the operation that was performed on the
   *     data.
   */
  FaasDatasourceSemanticConvention setFaasDocumentOperation(String faasDocumentOperation);

  /**
   * Sets a value for faas.document.time
   *
   * @param faasDocumentTime A string containing the time when the data was accessed in the [ISO
   *     8601](https://www.iso.org/iso-8601-date-and-time-format.html) format expressed in
   *     [UTC](https://www.w3.org/TR/NOTE-datetime).
   */
  FaasDatasourceSemanticConvention setFaasDocumentTime(String faasDocumentTime);

  /**
   * Sets a value for faas.document.name
   *
   * @param faasDocumentName The document name/table subjected to the operation.
   *     <p>For example, in Cloud Storage or S3 is the name of the file, and in Cosmos DB the table
   *     name.
   */
  FaasDatasourceSemanticConvention setFaasDocumentName(String faasDocumentName);
}
