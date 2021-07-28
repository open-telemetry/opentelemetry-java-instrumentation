/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0;

import io.opentelemetry.instrumentation.servlet.v3_0.Servlet3HttpServerTracer;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatServerHandlerAdviceHelper;
import net.bytebuddy.asm.Advice;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public class Tomcat7AttachResponseAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void attachResponse(
      @Advice.Argument(0) Request request,
      @Advice.Argument(2) Response response,
      @Advice.Return boolean success) {

    if (success) {
      TomcatServerHandlerAdviceHelper.attachResponseToRequest(
          Tomcat7ServletEntityProvider.INSTANCE,
          Servlet3HttpServerTracer.tracer(),
          request,
          response);
    }
  }
}
