/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ExtendWith(SystemStubsExtension.class)
class AwsXrayEnvSpanLinksExtractorTest {
  private static final Attributes EXPECTED_LINK_ATTRIBUTES =
      Attributes.of(AttributeKey.stringKey("source"), "x-ray-env");

  @SystemStub final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @Test
  void shouldIgnoreIfEnvVarEmpty() {
    // given
    SpanLinksBuilder spanLinksBuilder = mock(SpanLinksBuilder.class);
    environmentVariables.set("_X_AMZN_TRACE_ID", "");

    // when
    AwsXrayEnvSpanLinksExtractor.extract(spanLinksBuilder);
    // then
    verifyNoInteractions(spanLinksBuilder);
  }

  @Test
  void shouldLinkAwsParentHeaderIfValidAndNotSampled() {
    // given
    SpanLinksBuilder spanLinksBuilder = mock(SpanLinksBuilder.class);
    environmentVariables.set(
        "_X_AMZN_TRACE_ID",
        "Root=1-8a3c60f7-d188f8fa79d48a391a778fa6;Parent=0000000000000456;Sampled=0");

    // when
    AwsXrayEnvSpanLinksExtractor.extract(spanLinksBuilder);
    // then
    ArgumentCaptor<SpanContext> captor = ArgumentCaptor.forClass(SpanContext.class);
    verify(spanLinksBuilder).addLink(captor.capture(), eq(EXPECTED_LINK_ATTRIBUTES));
    SpanContext spanContext = captor.getValue();
    assertThat(spanContext.isValid()).isTrue();
    assertThat(spanContext.isSampled()).isFalse();
    assertThat(spanContext.getSpanId()).isEqualTo("0000000000000456");
    assertThat(spanContext.getTraceId()).isEqualTo("8a3c60f7d188f8fa79d48a391a778fa6");
  }

  @Test
  void shouldLinkAwsParentHeaderIfValidAndSampled() {
    // given
    SpanLinksBuilder spanLinksBuilder = mock(SpanLinksBuilder.class);
    environmentVariables.set(
        "_X_AMZN_TRACE_ID",
        "Root=1-8a3c60f7-d188f8fa79d48a391a778fa6;Parent=0000000000000456;Sampled=1");

    // when
    AwsXrayEnvSpanLinksExtractor.extract(spanLinksBuilder);
    // then
    ArgumentCaptor<SpanContext> captor = ArgumentCaptor.forClass(SpanContext.class);
    verify(spanLinksBuilder).addLink(captor.capture(), eq(EXPECTED_LINK_ATTRIBUTES));
    SpanContext spanContext = captor.getValue();
    assertThat(spanContext.isValid()).isTrue();
    assertThat(spanContext.isSampled()).isTrue();
    assertThat(spanContext.getSpanId()).isEqualTo("0000000000000456");
    assertThat(spanContext.getTraceId()).isEqualTo("8a3c60f7d188f8fa79d48a391a778fa6");
  }
}
