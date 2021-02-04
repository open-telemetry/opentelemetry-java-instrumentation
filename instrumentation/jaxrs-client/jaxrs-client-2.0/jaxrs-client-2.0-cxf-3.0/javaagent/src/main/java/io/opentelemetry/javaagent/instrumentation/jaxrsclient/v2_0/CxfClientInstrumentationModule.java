/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.cxf.message.Message;

/**
 * JAX-RS Client API doesn't define a good point where we can handle connection failures, so we must
 * handle these errors at the implementation level.
 */
@AutoService(InstrumentationModule.class)
public class CxfClientInstrumentationModule extends InstrumentationModule {

  public CxfClientInstrumentationModule() {
    super("jaxrs-client", "jaxrs-client-2.0", "cxf-client", "cxf-client-3.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new CxfClientConnectionErrorInstrumentation(),
        new CxfAsyncClientConnectionErrorInstrumentation());
  }

  public static class CxfClientConnectionErrorInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.apache.cxf.jaxrs.client.AbstractClient");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return Collections.singletonMap(
          named("preProcessResult").and(takesArgument(0, named("org.apache.cxf.message.Message"))),
          CxfClientInstrumentationModule.class.getName() + "$ErrorAdvice");
    }
  }

  public static class CxfAsyncClientConnectionErrorInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.apache.cxf.jaxrs.client.JaxrsClientCallback");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return Collections.singletonMap(
          named("handleException")
              .and(
                  takesArgument(0, named(Map.class.getName()))
                      .and(takesArgument(1, named(Throwable.class.getName())))),
          CxfClientInstrumentationModule.class.getName() + "$AsyncErrorAdvice");
    }
  }

  public static class ErrorAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void handleError(
        @Advice.Argument(0) Message message, @Advice.Thrown Throwable throwable) {
      if (throwable != null) {
        CxfClientUtil.handleException(message, throwable);
      }
    }
  }

  public static class AsyncErrorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void handleError(
        @Advice.Argument(0) Map<String, Object> map, @Advice.Argument(1) Throwable throwable) {
      if (throwable != null && map instanceof Message) {
        CxfClientUtil.handleException((Message) map, throwable);
      }
    }
  }
}
