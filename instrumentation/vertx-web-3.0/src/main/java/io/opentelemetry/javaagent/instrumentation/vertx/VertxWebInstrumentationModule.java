/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class VertxWebInstrumentationModule extends InstrumentationModule {

  public VertxWebInstrumentationModule() {
    super("vertx-web", "vertx-web-3.0", "vertx");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".RoutingContextHandlerWrapper", packageName + ".VertxTracer",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RouteInstrumentation());
  }

  private static final class RouteInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("io.vertx.ext.web.Route");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return not(isInterface()).and(safeHasSuperType(named("io.vertx.ext.web.Route")));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod().and(named("handler")).and(takesArgument(0, named("io.vertx.core.Handler"))),
          VertxWebInstrumentationModule.class.getName() + "$RouteAdvice");
    }
  }

  public static class RouteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false) Handler<RoutingContext> handler) {
      handler = new RoutingContextHandlerWrapper(handler);
    }
  }
}
