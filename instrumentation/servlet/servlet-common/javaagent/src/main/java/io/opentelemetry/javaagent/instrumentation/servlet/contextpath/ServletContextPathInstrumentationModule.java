/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.contextpath;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ServletContextPathInstrumentationModule extends InstrumentationModule {
  public ServletContextPathInstrumentationModule() {
    super("servlet", "servlet-context-path");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ServletContextPathInstrumentation());
  }

  @Override
  public int getOrder() {
    // run after Servlet3InstrumentationModule/Servlet2InstrumentationModule
    // so we warp the context created by servlet integration
    return 1;
  }

  public static class ServletContextPathInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("javax.servlet.Filter");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return safeHasSuperType(namedOneOf("javax.servlet.Filter", "javax.servlet.Servlet"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          namedOneOf("doFilter", "service")
              .and(takesArgument(0, named("javax.servlet.ServletRequest")))
              .and(takesArgument(1, named("javax.servlet.ServletResponse")))
              .and(isPublic()),
          ServletContextPathAdvice.class.getName());
    }
  }

  public static class ServletContextPathAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) ServletRequest request, @Advice.Local("otelScope") Scope scope) {

      if (!(request instanceof HttpServletRequest)) {
        return;
      }

      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(ServletContextPath.class);
      if (callDepth > 0) {
        return;
      }

      HttpServletRequest httpServletRequest = (HttpServletRequest) request;

      String contextPath = httpServletRequest.getContextPath();
      if (contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/")) {
        Context context =
            Java8BytecodeBridge.currentContext().with(ServletContextPath.CONTEXT_KEY, contextPath);
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stop(@Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        CallDepthThreadLocalMap.reset(ServletContextPath.class);
        scope.close();
      }
    }
  }
}
