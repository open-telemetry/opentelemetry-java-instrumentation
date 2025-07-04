/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import static io.opentelemetry.javaagent.instrumentation.tomcat.v10_0.Tomcat10Singletons.helper;

import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import org.apache.coyote.Response;

@SuppressWarnings("unused")
public class Tomcat10AttachResponseAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void attachResponse(
      @Advice.Argument(2) Response response, @Advice.Return boolean success) {

    if (success) {
      helper().attachResponseToRequest(Java8BytecodeBridge.currentContext(), response);
    }
  }
}
