/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

class DynamoDbAttributesGetterTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "ResourceNotFoundException"})
  void terminalServiceErrorType(String errorCode) {
    AwsServiceException error =
        DynamoDbException.builder()
            .awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).build())
            .build();
    AttributesBuilder attributes = Attributes.builder().put(ERROR_TYPE, "earlier-http-attempt");

    DbClientAttributesExtractor.create(new DynamoDbAttributesGetter())
        .onEnd(attributes, Context.root(), new ExecutionAttributes(), null, error);

    assertThat(attributes.build().get(ERROR_TYPE))
        .isEqualTo(
            errorCode == null || errorCode.trim().isEmpty()
                ? error.getClass().getName()
                : errorCode);
  }
}
