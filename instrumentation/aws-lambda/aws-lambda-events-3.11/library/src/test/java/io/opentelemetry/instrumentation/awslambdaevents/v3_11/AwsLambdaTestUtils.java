/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v3_11;

import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import java.lang.reflect.Method;

final class AwsLambdaTestUtils {

  private static final String INVOKED_FUNCTION_ARN =
      "arn:aws:lambda:us-east-1:123456789:function:test";

  static void stubInvokedFunctionArn(Context context) {
    try {
      Method method = Context.class.getMethod("getInvokedFunctionArn");
      when((String) method.invoke(context)).thenReturn(INVOKED_FUNCTION_ARN);
    } catch (NoSuchMethodException ignored) {
      // getInvokedFunctionArn was added in aws-lambda-java-core 1.2.1.
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  static String expectedCloudResourceId() {
    return supportsInvokedFunctionArn() ? INVOKED_FUNCTION_ARN : null;
  }

  static String expectedCloudAccountId() {
    return supportsInvokedFunctionArn() ? "123456789" : null;
  }

  private static boolean supportsInvokedFunctionArn() {
    try {
      Context.class.getMethod("getInvokedFunctionArn");
      return true;
    } catch (NoSuchMethodException ignored) {
      return false;
    }
  }

  private AwsLambdaTestUtils() {}
}
