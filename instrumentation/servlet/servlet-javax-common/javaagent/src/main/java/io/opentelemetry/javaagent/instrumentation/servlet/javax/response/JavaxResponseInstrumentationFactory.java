/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.javax.response;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.HttpServletResponseInstrumentation;

public final class JavaxResponseInstrumentationFactory {

  private static final String BASE_PACKAGE = "javax.servlet";

  public static TypeInstrumentation create() {
    return new HttpServletResponseInstrumentation(
        BASE_PACKAGE,
        JavaxResponseInstrumentationFactory.class.getPackage().getName() + ".ResponseSendAdvice");
  }

  private JavaxResponseInstrumentationFactory() {}
}
