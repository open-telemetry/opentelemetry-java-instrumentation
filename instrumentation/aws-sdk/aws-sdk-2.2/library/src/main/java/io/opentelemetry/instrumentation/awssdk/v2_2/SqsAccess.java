/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

// helper class for calling methods that use sqs types in SqsImpl
// if SqsImpl is not present these methods are no op
final class SqsAccess {
  private SqsAccess() {}

  private static final boolean enabled = isSqsImplPresent();

  private static boolean isSqsImplPresent() {
    try {
      // for library instrumentation SqsImpl is always available
      // for javaagent instrumentation SqsImpl is available only when SqsInstrumentationModule was
      // successfully applied (muzzle passed)
      // using package name here because library instrumentation classes are relocated when embedded
      // in the agent
      Class.forName(SqsAccess.class.getName().replace(".SqsAccess", ".SqsImpl"));
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @NoMuzzle
  static boolean isSendMessageRequest(SdkRequest request) {
    return enabled && request instanceof SendMessageRequest;
  }

  @NoMuzzle
  static SdkRequest injectIntoSendMessageRequest(
      TextMapPropagator messagingPropagator,
      SdkRequest rawRequest,
      io.opentelemetry.context.Context otelContext) {
    assert enabled; // enabled checked already in instance check.
    return SqsImpl.injectIntoSendMessageRequest(messagingPropagator, rawRequest, otelContext);
  }

  @NoMuzzle
  static boolean isReceiveMessageRequest(SdkRequest request) {
    return enabled && request instanceof ReceiveMessageRequest;
  }

  @NoMuzzle
  public static SdkRequest modifyReceiveMessageRequest(
      SdkRequest request, boolean useXrayPropagator, TextMapPropagator messagingPropagator) {
    assert enabled; // enabled checked already in instance check.
    return SqsImpl.modifyReceiveMessageRequest(request, useXrayPropagator, messagingPropagator);
  }

  @NoMuzzle
  static void afterReceiveMessageExecution(
      TracingExecutionInterceptor config,
      Context.AfterExecution context,
      ExecutionAttributes executionAttributes) {
    assert enabled; // enabled checked already in instance check.
    SqsImpl.afterReceiveMessageExecution(config, executionAttributes, context);
  }
}
