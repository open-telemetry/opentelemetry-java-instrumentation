/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11

import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import io.opentelemetry.instrumentation.test.LibraryTestTrait

class SqsTracingTest extends AbstractSqsTracingTest implements LibraryTestTrait {
  @Override
  AmazonSQSAsyncClientBuilder configureClient(AmazonSQSAsyncClientBuilder client) {
    return client.withRequestHandlers(
      AwsSdkTracing.builder(getOpenTelemetry())
        .setCaptureExperimentalSpanAttributes(true)
        .build()
        .newRequestHandler())
  }
}
