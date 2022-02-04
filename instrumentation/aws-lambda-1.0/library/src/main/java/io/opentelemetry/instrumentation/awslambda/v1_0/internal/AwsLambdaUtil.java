/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsLambdaUtil {

  private static final boolean HAS_EVENTS;

  static {
    boolean hasEvents = false;
    try {
      Class.forName("com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent");
      hasEvents = true;
    } catch (Throwable t) {
      // Ignore.
    }
    HAS_EVENTS = hasEvents;
  }

  /** Returns whether events are available on the classpath. */
  public static boolean hasEvents() {
    return HAS_EVENTS;
  }

  private AwsLambdaUtil() {}
}
