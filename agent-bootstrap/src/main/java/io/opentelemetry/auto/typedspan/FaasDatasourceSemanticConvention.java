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

public interface FaasDatasourceSemanticConvention {
  void end();

  Span getSpan();

  /**
   * Sets a value for faas.trigger
   *
   * @param faasTrigger Type of the trigger on which the function is executed..
   */
  public FaasDatasourceSemanticConvention setFaasTrigger(String faasTrigger);

  /**
   * Sets a value for faas.execution
   *
   * @param faasExecution The execution id of the current function execution..
   */
  public FaasDatasourceSemanticConvention setFaasExecution(String faasExecution);

  /**
   * Sets a value for faas.document.collection
   *
   * @param faasDocumentCollection The name of the source on which the triggering operation was
   *     performed..
   *     <p>For example, in Cloud Storage or S3 corresponds to the bucket name, and in Cosmos DB to
   *     the database name.
   */
  public FaasDatasourceSemanticConvention setFaasDocumentCollection(String faasDocumentCollection);

  /**
   * Sets a value for faas.document.operation
   *
   * @param faasDocumentOperation Describes the type of the operation that was performed on the
   *     data..
   */
  public FaasDatasourceSemanticConvention setFaasDocumentOperation(String faasDocumentOperation);

  /**
   * Sets a value for faas.document.time
   *
   * @param faasDocumentTime A string containing the time when the data was accessed in the [ISO
   *     8601](https://www.iso.org/iso-8601-date-and-time-format.html) format expressed in
   *     [UTC](https://www.w3.org/TR/NOTE-datetime)..
   */
  public FaasDatasourceSemanticConvention setFaasDocumentTime(String faasDocumentTime);

  /**
   * Sets a value for faas.document.name
   *
   * @param faasDocumentName The document name/table subjected to the operation..
   *     <p>For example, in Cloud Storage or S3 is the name of the file, and in Cosmos DB the table
   *     name.
   */
  public FaasDatasourceSemanticConvention setFaasDocumentName(String faasDocumentName);
}
