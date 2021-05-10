/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Map;
import javax.ws.rs.core.Request;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JerseyResourceMethodDispatcherInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.jersey.server.model.internal.AbstractJavaResourceMethodDispatcher");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("dispatch")
            .and(
                takesArgument(
                    1,
                    named("javax.ws.rs.core.Request")
                        .or(named("org.glassfish.jersey.server.ContainerRequest")))),
        JerseyResourceMethodDispatcherInstrumentation.class.getName() + "$DispatchAdvice");
  }

  public static class DispatchAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(1) Request request) {
      JerseyTracingUtil.updateServerSpanName(request);
    }
  }
}
