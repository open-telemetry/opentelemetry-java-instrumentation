/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_BEDROCK_GUARDRAIL_ID;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailStreamConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;

class BedrockRuntimeAttributesExtractorTest {

  private final BedrockRuntimeAttributesExtractor extractor =
      new BedrockRuntimeAttributesExtractor();

  @ParameterizedTest
  @MethodSource("invokeModelRequests")
  void extractsTopLevelGuardrailIdentifier(SdkRequest request) {
    assertThat(extract(request).get(AWS_BEDROCK_GUARDRAIL_ID)).isEqualTo("guardrail-id");
  }

  private static Stream<Arguments> invokeModelRequests() {
    return Stream.of(
        argumentSet(
            "InvokeModel",
            InvokeModelRequest.builder()
                .guardrailIdentifier("guardrail-id")
                .guardrailVersion("1")
                .build()),
        argumentSet(
            "InvokeModelWithResponseStream",
            InvokeModelWithResponseStreamRequest.builder()
                .guardrailIdentifier("guardrail-id")
                .guardrailVersion("1")
                .build()));
  }

  @Test
  void extractsNestedGuardrailIdentifier() {
    SdkRequest request = mock(SdkRequest.class);
    SdkPojo guardrailConfig = mock(SdkPojo.class);
    @SuppressWarnings("unchecked")
    SdkField<String> guardrailIdentifierField = mock(SdkField.class);
    when(request.getValueForField("guardrailIdentifier", String.class))
        .thenReturn(Optional.empty());
    when(request.getValueForField("guardrailConfig", Object.class))
        .thenReturn(Optional.of(guardrailConfig));
    when(guardrailConfig.sdkFields()).thenReturn(singletonList(guardrailIdentifierField));
    when(guardrailIdentifierField.memberName()).thenReturn("guardrailIdentifier");
    when(guardrailIdentifierField.getValueOrDefault(guardrailConfig)).thenReturn("guardrail-id");

    assertThat(extract(request).get(AWS_BEDROCK_GUARDRAIL_ID)).isEqualTo("guardrail-id");
  }

  @ParameterizedTest
  @MethodSource("converseRequests")
  void extractsGuardrailIdentifierFromConverseRequest(boolean streaming) {
    assumeTrue(testLatestDeps());

    assertThat(extract(LatestConverseRequests.create(streaming)).get(AWS_BEDROCK_GUARDRAIL_ID))
        .isEqualTo("guardrail-id");
  }

  private static Stream<Arguments> converseRequests() {
    return Stream.of(argumentSet("Converse", false), argumentSet("ConverseStream", true));
  }

  @Test
  void omitsGuardrailIdentifierWhenNotConfigured() {
    SdkRequest request = mock(SdkRequest.class);
    when(request.getValueForField("guardrailIdentifier", String.class))
        .thenReturn(Optional.empty());
    when(request.getValueForField("guardrailConfig", Object.class)).thenReturn(Optional.empty());

    assertThat(extract(request).asMap()).isEmpty();
  }

  private Attributes extract(SdkRequest request) {
    ExecutionAttributes executionAttributes =
        new ExecutionAttributes()
            .putAttribute(TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE, request);
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), executionAttributes);
    return attributes.build();
  }

  // Keep references to types introduced after the floor version isolated from floor-version runs.
  private static final class LatestConverseRequests {
    private static SdkRequest create(boolean streaming) {
      if (streaming) {
        return ConverseStreamRequest.builder()
            .modelId("model-id")
            .guardrailConfig(
                GuardrailStreamConfiguration.builder()
                    .guardrailIdentifier("guardrail-id")
                    .guardrailVersion("1")
                    .build())
            .build();
      }
      return ConverseRequest.builder()
          .modelId("model-id")
          .guardrailConfig(
              GuardrailConfiguration.builder()
                  .guardrailIdentifier("guardrail-id")
                  .guardrailVersion("1")
                  .build())
          .build();
    }

    private LatestConverseRequests() {}
  }
}
