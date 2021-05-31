/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.cxf.message.Message;

public class CxfAsyncClientConnectionErrorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.cxf.jaxrs.client.JaxrsClientCallback");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handleException")
            .and(
                takesArgument(0, named(Map.class.getName()))
                    .and(takesArgument(1, named(Throwable.class.getName())))),
        this.getClass().getName() + "$HandleExceptionAdvice");
  }

  public static class HandleExceptionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void handleError(
        @Advice.Argument(0) Map<String, Object> map, @Advice.Argument(1) Throwable throwable) {
      if (throwable != null && map instanceof Message) {
        CxfClientUtil.handleException((Message) map, throwable);
      }
    }
  }
}
