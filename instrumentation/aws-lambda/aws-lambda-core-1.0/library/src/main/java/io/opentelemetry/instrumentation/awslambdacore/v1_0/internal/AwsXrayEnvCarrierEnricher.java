/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import java.util.HashMap;
import java.util.Map;

/**
 * Enrich carrier with AWS Lambda runtime trace information.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class AwsXrayEnvCarrierEnricher {

  private static final String AWS_TRACE_ENV_VAR = "_X_AMZN_TRACE_ID";
  private static final String AWS_TRACE_PROPERTY = "com.amazonaws.xray.traceHeader";
  private static final String AWS_TRACE_PROPAGATOR_KEY = "x-amzn-trace-id";

  public Map<String, String> enrichFrom(Map<String, String> carrier) {
    HashMap<String, String> newCarrier = new HashMap<>();
    if (carrier != null) {
      newCarrier.putAll(carrier);
    }

    String xrayTrace = System.getProperty(AWS_TRACE_PROPERTY);

    xrayTrace = isEmptyOrNull(xrayTrace) ? System.getenv(AWS_TRACE_ENV_VAR) : xrayTrace;

    if (!isEmptyOrNull(xrayTrace)) {
      newCarrier.put(AWS_TRACE_PROPAGATOR_KEY, xrayTrace);
    }
    return newCarrier;
  }

  private static boolean isEmptyOrNull(String value) {
    return value == null || value.isEmpty();
  }
}
