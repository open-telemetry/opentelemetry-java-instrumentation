/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import com.amazonaws.services.lambda.runtime.Context
import io.opentelemetry.instrumentation.test.LibraryInstrumentationSpecification
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import spock.lang.Shared 

class TracingRequestWrapperTestBase extends LibraryInstrumentationSpecification {

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  @Shared
  TracingRequestWrapperBase wrapper

  @Shared
  Context context

  def setup() {
    context = Mock(Context)
    context.getFunctionName() >> "my_function"
    context.getAwsRequestId() >> "1-22-333"
    context.getInvokedFunctionArn() >> "arn:aws:lambda:us-east-1:123456789:function:test"
  }

  def setLambda(handler, Closure<TracingRequestWrapperBase> wrapperConstructor) {
    environmentVariables.set(WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY, handler)
    TracingRequestWrapper.WRAPPED_LAMBDA = WrappedLambda.fromConfiguration()
    wrapper = wrapperConstructor.call(testRunner().openTelemetrySdk)
  }
}
