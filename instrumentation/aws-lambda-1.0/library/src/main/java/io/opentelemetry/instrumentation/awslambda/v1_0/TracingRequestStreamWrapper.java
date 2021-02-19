/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Wrapper for {@link TracingRequestStreamHandler}. Allows for wrapping a regular lambda, enabling
 * single span tracing. Main lambda class should be configured as env property
 * OTEL_INSTRUMENTATION_AWS_LAMBDA_HANDLER in package.ClassName::methodName format. Lambda class
 * must implement {@link RequestStreamHandler}.
 */
public class TracingRequestStreamWrapper extends TracingRequestStreamHandler {

  // visible for testing
  static WrappedLambda WRAPPED_LAMBDA = WrappedLambda.fromConfiguration();

  public TracingRequestStreamWrapper() {
    this(OpenTelemetrySdkAutoConfiguration.initialize());
  }

  // Visible for testing
  TracingRequestStreamWrapper(OpenTelemetrySdk openTelemetrySdk) {
    super(openTelemetrySdk, WrapperConfiguration.flushTimeout());
  }

  @Override
  protected void doHandleRequest(InputStream inputStream, OutputStream output, Context context)
      throws IOException {

    if (!(WRAPPED_LAMBDA.getTargetObject() instanceof RequestStreamHandler)) {
      throw new RuntimeException(
          WRAPPED_LAMBDA.getTargetClass().getName()
              + " is not an instance of RequestStreamHandler");
    }

    ((RequestStreamHandler) WRAPPED_LAMBDA.getTargetObject())
        .handleRequest(inputStream, output, context);
  }
}
