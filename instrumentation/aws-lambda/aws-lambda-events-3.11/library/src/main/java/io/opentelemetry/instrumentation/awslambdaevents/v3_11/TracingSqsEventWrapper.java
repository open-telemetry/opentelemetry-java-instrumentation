/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v3_11;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrappedLambda;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrapperConfiguration;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.LambdaParameters;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TracingSqsEventWrapper extends TracingSqsEventHandler {

  private final WrappedLambda wrappedLambda;
  private final Method targetMethod;

  public TracingSqsEventWrapper() {
    this(
        AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk(),
        WrappedLambda.fromConfiguration());
  }

  // Visible for testing
  TracingSqsEventWrapper(OpenTelemetrySdk openTelemetrySdk, WrappedLambda wrappedLambda) {
    super(openTelemetrySdk, WrapperConfiguration.flushTimeout());
    this.wrappedLambda = wrappedLambda;
    this.targetMethod = wrappedLambda.getRequestTargetMethod();
  }

  @Override
  protected SQSBatchResponse handleEvent(SQSEvent sqsEvent, Context context) {
    Object[] parameters =
        LambdaParameters.toArray(targetMethod, sqsEvent, context, (event, clazz) -> event);
    try {
      Object result = targetMethod.invoke(wrappedLambda.getTargetObject(), parameters);
      return result instanceof SQSBatchResponse ? (SQSBatchResponse) result : null;
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Method is inaccessible", e);
    } catch (InvocationTargetException e) {
      throw (e.getCause() instanceof RuntimeException
          ? (RuntimeException) e.getCause()
          : new IllegalStateException(e.getTargetException()));
    }
  }
}
