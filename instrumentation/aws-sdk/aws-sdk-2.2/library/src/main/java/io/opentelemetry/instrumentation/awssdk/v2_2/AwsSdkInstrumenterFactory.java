/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.util.Arrays;
import java.util.List;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpResponse;

final class AwsSdkInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.aws-sdk-2.2";
  static final AwsSdkHttpAttributesExtractor httpAttributesExtractor =
      new AwsSdkHttpAttributesExtractor();
  static final AwsSdkRpcAttributesExtractor rpcAttributesExtractor =
      new AwsSdkRpcAttributesExtractor();
  private static final AwsSdkNetAttributesAdapter netAttributesAdapter =
      new AwsSdkNetAttributesAdapter();
  private static final NetClientAttributesExtractor<ExecutionAttributes, SdkHttpResponse> netAttributesExtractor = NetClientAttributesExtractor.create(netAttributesAdapter);
  private static final AwsSdkExperimentalAttributesExtractor experimentalAttributesExtractor =
      new AwsSdkExperimentalAttributesExtractor();

  private static final List<AttributesExtractor<ExecutionAttributes, SdkHttpResponse>>
      defaultAttributesExtractors =
          Arrays.asList(httpAttributesExtractor, rpcAttributesExtractor, netAttributesExtractor);

  private static final List<AttributesExtractor<ExecutionAttributes, SdkHttpResponse>>
      extendedAttributesExtractors =
          Arrays.asList(
              httpAttributesExtractor,
              rpcAttributesExtractor,
              netAttributesExtractor,
              experimentalAttributesExtractor);

  static Instrumenter<ExecutionAttributes, SdkHttpResponse> createInstrumenter(
      OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {

    return Instrumenter.<ExecutionAttributes, SdkHttpResponse>builder(
            openTelemetry, INSTRUMENTATION_NAME, AwsSdkInstrumenterFactory::spanName)
        .addAttributesExtractors(
            captureExperimentalSpanAttributes
                ? extendedAttributesExtractors
                : defaultAttributesExtractors)
        .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private static String spanName(ExecutionAttributes attributes) {
    String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    return awsServiceName + "." + awsOperation;
  }

  private AwsSdkInstrumenterFactory() {}
}
