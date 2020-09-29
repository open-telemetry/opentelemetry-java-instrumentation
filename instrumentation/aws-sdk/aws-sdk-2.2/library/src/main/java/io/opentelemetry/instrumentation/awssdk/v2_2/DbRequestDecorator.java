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
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

final class DbRequestDecorator implements SdkRequestDecorator {

  @Override
  public void decorate(Span span, SdkRequest sdkRequest, ExecutionAttributes attributes) {

    span.setAttribute(SemanticAttributes.DB_SYSTEM, "dynamodb");
    // decorate with TableName as db.name (DynamoDB equivalent - not for batch)
    sdkRequest
        .getValueForField("TableName", String.class)
        .ifPresent(val -> span.setAttribute(SemanticAttributes.DB_NAME, val));

    String operation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    if (operation != null) {
      span.setAttribute(SemanticAttributes.DB_OPERATION, operation);
    }
  }
}
