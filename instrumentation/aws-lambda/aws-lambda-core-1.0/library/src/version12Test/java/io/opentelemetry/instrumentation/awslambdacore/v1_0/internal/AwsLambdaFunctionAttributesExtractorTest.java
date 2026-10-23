/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import static io.opentelemetry.semconv.incubating.CloudIncubatingAttributes.CLOUD_RESOURCE_ID;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import org.junit.jupiter.api.Test;

class AwsLambdaFunctionAttributesExtractorTest {

  @Test
  void resolvesAliasToFunctionVersion() {
    assertThat(resourceId("arn:aws:lambda:us-east-1:123456789:function:test:prod", "42"))
        .isEqualTo("arn:aws:lambda:us-east-1:123456789:function:test:42");
  }

  @Test
  void preservesNumericVersionQualifier() {
    assertThat(resourceId("arn:aws:lambda:us-east-1:123456789:function:test:42", "43"))
        .isEqualTo("arn:aws:lambda:us-east-1:123456789:function:test:42");
  }

  @Test
  void preservesUnqualifiedArn() {
    assertThat(resourceId("arn:aws:lambda:us-east-1:123456789:function:test", "42"))
        .isEqualTo("arn:aws:lambda:us-east-1:123456789:function:test");
  }

  @Test
  void preservesAliasWhenFunctionVersionIsUnavailable() {
    assertThat(resourceId("arn:aws:lambda:us-east-1:123456789:function:test:prod", null))
        .isEqualTo("arn:aws:lambda:us-east-1:123456789:function:test:prod");
  }

  private static String resourceId(String arn, String functionVersion) {
    Context awsContext = mock(Context.class);
    when(awsContext.getAwsRequestId()).thenReturn("request-id");
    when(awsContext.getInvokedFunctionArn()).thenReturn(arn);
    when(awsContext.getFunctionVersion()).thenReturn(functionVersion);

    AttributesBuilder attributesBuilder = Attributes.builder();
    new AwsLambdaFunctionAttributesExtractor()
        .onStart(
            attributesBuilder,
            io.opentelemetry.context.Context.root(),
            AwsLambdaRequest.create(awsContext, new Object(), emptyMap()));
    Attributes attributes = attributesBuilder.build();
    return attributes.get(CLOUD_RESOURCE_ID);
  }
}
