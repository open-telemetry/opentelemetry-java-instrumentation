/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.servlet.ServletSpanNaming;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.wicket.core.request.handler.IPageClassRequestHandler;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.cycle.RequestCycle;

public class RequestHandlerExecutorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.apache.wicket.request.RequestHandlerExecutor");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        named("execute").and(takesArgument(0, named("org.apache.wicket.request.IRequestHandler"))),
        RequestHandlerExecutorInstrumentation.class.getName() + "$ExecuteAdvice");
  }

  public static class ExecuteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onExit(@Advice.Argument(0) IRequestHandler handler) {
      Context context = Java8BytecodeBridge.currentContext();
      Span serverSpan = ServerSpan.fromContextOrNull(context);
      if (serverSpan == null) {
        return;
      }
      if (handler instanceof IPageClassRequestHandler) {
        // using class name as page name
        String pageName = ((IPageClassRequestHandler) handler).getPageClass().getName();
        // wicket filter mapping without wildcard, if wicket filter is mapped to /*
        // this will be an empty string
        String filterPath = RequestCycle.get().getRequest().getFilterPath();
        serverSpan.updateName(ServletContextPath.prepend(context, filterPath + "/" + pageName));
        // prevent servlet integration from doing further updates to server span name
        ServletSpanNaming.setServletUpdatedServerSpanName(context);
      }
    }
  }
}
