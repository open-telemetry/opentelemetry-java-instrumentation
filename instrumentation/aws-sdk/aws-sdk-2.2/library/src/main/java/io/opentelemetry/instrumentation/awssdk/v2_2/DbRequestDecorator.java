/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
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
