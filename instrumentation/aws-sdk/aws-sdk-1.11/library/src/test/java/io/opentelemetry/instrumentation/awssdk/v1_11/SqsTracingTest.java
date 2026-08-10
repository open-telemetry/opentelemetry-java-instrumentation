/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SqsTracingTest extends AbstractSqsTracingTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  public AmazonSQSAsyncClientBuilder configureClient(AmazonSQSAsyncClientBuilder client) {
    return client.withRequestHandlers(
        AwsSdkTelemetry.builder(testing().getOpenTelemetry())
            .setCaptureExperimentalSpanAttributes(true)
            .setMessagingReceiveTelemetryEnabled(true)
            .setCapturedHeaders(singletonList("Test-Message-Header"))
            .build()
            .createRequestHandler());
  }

  @Test
  void testDisableSqsMessageCreateSpans() {
    assumeTrue(emitStableMessagingSemconv());
    AmazonSQSAsync client =
        newClientBuilder()
            .withRequestHandlers(
                AwsSdkTelemetry.builder(testing().getOpenTelemetry())
                    .setCaptureExperimentalSpanAttributes(true)
                    .setSqsMessageCreateSpansEnabled(false)
                    .build()
                    .createRequestHandler())
            .build();
    try {
      String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
      client.createQueue("testSdkSqs");
      client.sendMessageBatch(
          new SendMessageBatchRequest()
              .withQueueUrl(queueUrl)
              .withEntries(
                  new SendMessageBatchRequestEntry("i1", "e1"),
                  new SendMessageBatchRequestEntry("i2", "e2")));

      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("SQS.CreateQueue").hasKind(SpanKind.CLIENT)),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("send testSdkSqs")
                              .hasKind(SpanKind.PRODUCER)
                              .hasNoParent()
                              .hasTotalRecordedLinks(0)));
    } finally {
      client.shutdown();
    }
  }
}
