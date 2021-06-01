package com.example.javaagent.instrumentation;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.safeHasSuperType;
import static io.opentelemetry.javaagent.extension.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.*;

public class DemoServlet3Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(namedOneOf("javax.servlet.Filter", "javax.servlet.http.HttpServlet"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        namedOneOf("doFilter", "service")
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
            .and(isPublic()),
        this.getClass().getName() + "$DemoServlet3Advice");
  }

  @SuppressWarnings("unused")
  public static class DemoServlet3Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 1) ServletResponse response) {
      if (!(response instanceof HttpServletResponse)) {
        return;
      }

      HttpServletResponse httpServletResponse = (HttpServletResponse) response;
      if (!httpServletResponse.containsHeader("X-server-id")) {
        httpServletResponse.setHeader(
            "X-server-id", Java8BytecodeBridge.currentSpan().getSpanContext().getTraceId());
      }
    }
  }
}
