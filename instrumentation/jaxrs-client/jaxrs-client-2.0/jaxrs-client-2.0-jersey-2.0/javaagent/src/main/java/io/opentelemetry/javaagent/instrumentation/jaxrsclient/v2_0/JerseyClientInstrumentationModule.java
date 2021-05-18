/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Collections;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.OpenTelemetryResponseCallbackWrapper;

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
  public boolean isHelperClass(String className) {
    return className.equals("org.glassfish.jersey.client.OpenTelemetryResponseCallbackWrapper");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new JerseyClientConnectionErrorInstrumentation());
  }

  public static class JerseyClientConnectionErrorInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.glassfish.jersey.client.ClientRuntime");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          isMethod()
              .and(isPublic())
              .and(named("invoke"))
              .and(takesArgument(0, named("org.glassfish.jersey.client.ClientRequest"))),
          JerseyClientInstrumentationModule.class.getName() + "$InvokeAdvice");
      transformer.applyAdviceToMethod(
          isMethod()
              .and(named("submit").or(named("createRunnableForAsyncProcessing")))
              .and(takesArgument(0, named("org.glassfish.jersey.client.ClientRequest")))
              .and(takesArgument(1, named("org.glassfish.jersey.client.ResponseCallback"))),
          JerseyClientInstrumentationModule.class.getName() + "$SubmitAdvice");
    }
  }

  public static class InvokeAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void handleError(
        @Advice.Argument(0) ClientRequest context, @Advice.Thrown Throwable throwable) {
      if (throwable != null) {
        JerseyClientUtil.handleException(context, throwable);
      }
    }
  }

  public static class SubmitAdvice {

    // using dynamic typing because parameter type is package private
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void handleError(
        @Advice.Argument(0) ClientRequest context,
        @Advice.Argument(value = 1, readOnly = false, typing = Assigner.Typing.DYNAMIC)
            Object callback) {
      callback = OpenTelemetryResponseCallbackWrapper.wrap(context, callback);
    }
  }
}
