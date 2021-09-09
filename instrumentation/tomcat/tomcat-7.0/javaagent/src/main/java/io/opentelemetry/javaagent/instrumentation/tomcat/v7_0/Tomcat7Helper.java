/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0;

import static io.opentelemetry.javaagent.instrumentation.tomcat.v7_0.Tomcat7Singletons.instrumenter;

import io.opentelemetry.instrumentation.servlet.v3_0.Servlet3Helper;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatHelper;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Tomcat7Helper extends TomcatHelper<HttpServletRequest, HttpServletResponse> {
  private static final Tomcat7Helper HELPER = new Tomcat7Helper();

  public static Tomcat7Helper helper() {
    return HELPER;
  }

  private Tomcat7Helper() {
    super(instrumenter(), Tomcat7ServletEntityProvider.INSTANCE, Servlet3Helper.helper());
  }
}
