/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17.internal;

import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ContextDataAttributesResolver {

  private ContextDataAttributesResolver() {}

  public static boolean resolveCaptureAllContextDataAttributes(
      List<String> captureContextDataAttributes) {
    return captureContextDataAttributes.size() == 1
        && captureContextDataAttributes.get(0).equals("*");
  }
}
