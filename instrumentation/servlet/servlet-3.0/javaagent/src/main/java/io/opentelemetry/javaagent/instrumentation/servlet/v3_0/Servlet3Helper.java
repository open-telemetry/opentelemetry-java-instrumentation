/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.instrumenter;

import io.opentelemetry.instrumentation.servlet.v3_0.Servlet3Accessor;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet3Helper extends ServletHelper<HttpServletRequest, HttpServletResponse> {
  private static final Servlet3Helper HELPER = new Servlet3Helper();

  public static Servlet3Helper helper() {
    return HELPER;
  }

  private Servlet3Helper() {
    super(instrumenter(), Servlet3Accessor.INSTANCE);
  }
}
