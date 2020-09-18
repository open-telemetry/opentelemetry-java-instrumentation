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

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

final class DbRequestDecorator implements SdkRequestDecorator {

  private final DbRequestFactory dbRequestFactory = new DbRequestFactory();

  @Override
  public void decorate(Span span, SdkRequest sdkRequest, ExecutionAttributes attributes) {

    span.setAttribute(SemanticAttributes.DB_SYSTEM.key(), "dynamodb");
    // decorate with TableName as db.name (DynamoDB equivalent - not for batch)
    sdkRequest
        .getValueForField("TableName", String.class)
        .ifPresent(val -> span.setAttribute(SemanticAttributes.DB_NAME.key(), val));

    String operation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    if (operation != null) {
      SemanticAttributes.DB_OPERATION.set(span, operation);
    }
    String statement = statement(sdkRequest);
    if (statement != null) {
      SemanticAttributes.DB_STATEMENT.set(span, statement);
    }
  }

  @Nullable
  private String statement(SdkRequest sdkRequest) {
    DbRequest dbRequest = dbRequestFactory.valueOf(sdkRequest);
    return (dbRequest == null ? null : dbRequest.statement());
  }
}
