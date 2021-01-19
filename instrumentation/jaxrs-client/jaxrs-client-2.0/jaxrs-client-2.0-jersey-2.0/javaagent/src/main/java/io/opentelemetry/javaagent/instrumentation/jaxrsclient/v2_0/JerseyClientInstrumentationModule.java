/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.jersey.client.ClientRequest;

/**
 * JAX-RS Client API doesn't define a good point where we can handle connection failures, so we must
 * handle these errors at the implementation level.
 */
@AutoService(InstrumentationModule.class)
public class JerseyClientInstrumentationModule extends InstrumentationModule {

  public JerseyClientInstrumentationModule() {
    super("jaxrs-client", "jaxrs-client-2.0", "jersey-client", "jersey-client-2.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new JerseyClientConnectionErrorInstrumentation());
  }

  public static class JerseyClientConnectionErrorInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.glassfish.jersey.client.JerseyInvocation");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

      transformers.put(
          isMethod().and(isPublic()).and(named("invoke")),
          JerseyClientInstrumentationModule.class.getName() + "$InvokeAdvice");

      transformers.put(
          isMethod().and(isPublic()).and(named("submit")).and(returns(Future.class)),
          JerseyClientInstrumentationModule.class.getName() + "$SubmitAdvice");

      return transformers;
    }
  }

  public static class InvokeAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void handleError(
        @Advice.FieldValue("requestContext") ClientRequest context,
        @Advice.Thrown Throwable throwable) {
      if (throwable != null) {
        JerseyClientUtil.handleException(context, throwable);
      }
    }
  }

  public static class SubmitAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void handleError(
        @Advice.FieldValue("requestContext") ClientRequest context,
        @Advice.Return(readOnly = false) Future<?> future) {
      future = JerseyClientUtil.addErrorReporting(context, future);
    }
  }
}
