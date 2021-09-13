/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0;

import static io.opentelemetry.javaagent.instrumentation.tomcat.v7_0.Tomcat7Singletons.helper;

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
      helper().attachResponseToRequest(request, response);
    }
  }
}
