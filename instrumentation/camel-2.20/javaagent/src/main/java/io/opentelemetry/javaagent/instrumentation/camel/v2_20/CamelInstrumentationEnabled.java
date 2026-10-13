/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.camel.CamelContext;

public final class CamelInstrumentationEnabled {

  private static final VirtualField<CamelContext, Boolean> ENABLED =
      VirtualField.find(CamelContext.class, Boolean.class);

  public static void markEnabled(CamelContext context) {
    ENABLED.set(context, true);
  }

  public static boolean isEnabled(CamelContext context) {
    return Boolean.TRUE.equals(ENABLED.get(context));
  }

  private CamelInstrumentationEnabled() {}
}
