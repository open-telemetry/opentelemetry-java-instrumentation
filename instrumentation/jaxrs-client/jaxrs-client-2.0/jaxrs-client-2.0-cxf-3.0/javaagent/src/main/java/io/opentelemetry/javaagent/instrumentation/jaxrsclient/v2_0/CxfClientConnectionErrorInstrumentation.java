/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.cxf.message.Message;

public class CxfClientConnectionErrorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.cxf.jaxrs.client.AbstractClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("preProcessResult").and(takesArgument(0, named("org.apache.cxf.message.Message"))),
        this.getClass().getName() + "$PreProcessResultAdvice");
  }

  public static class PreProcessResultAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void handleError(
        @Advice.Argument(0) Message message, @Advice.Thrown Throwable throwable) {
      if (throwable != null) {
        CxfClientUtil.handleException(message, throwable);
      }
    }
  }
}
