/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class LibertyInstrumentationModule extends InstrumentationModule {

  public LibertyInstrumentationModule() {
    super("liberty");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(new WebAppInstrumentation(), new WebAppDispatcherContextInstrumentation());
  }

  public static class WebAppInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.ibm.ws.webcontainer.webapp.WebApp");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

      // https://github.com/OpenLiberty/open-liberty/blob/master/dev/com.ibm.ws.webcontainer/src/com/ibm/ws/webcontainer/webapp/WebApp.java
      transformers.put(
          named("handleRequest")
              .and(takesArgument(0, named("javax.servlet.ServletRequest")))
              .and(takesArgument(1, named("javax.servlet.ServletResponse")))
              .and(takesArgument(2, named("com.ibm.wsspi.http.HttpInboundConnection"))),
          LibertyHandleRequestAdvice.class.getName());

      // isForbidden is called from handleRequest
      transformers.put(
          named("isForbidden").and(takesArgument(0, named(String.class.getName()))),
          LibertyStartSpanAdvice.class.getName());

      return transformers;
    }
  }

  public static class WebAppDispatcherContextInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.ibm.ws.webcontainer.webapp.WebAppDispatcherContext");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      // https://github.com/OpenLiberty/open-liberty/blob/master/dev/com.ibm.ws.webcontainer/src/com/ibm/ws/webcontainer/webapp/WebAppDispatcherContext.java
      // after call to setPathElements we can use HttpServletRequest getServletPath and
      // getPathInfo
      // called during WebApp.handleRequest
      return singletonMap(
          named("setPathElements")
              .and(takesArgument(0, named(String.class.getName())))
              .and(takesArgument(1, named(String.class.getName()))),
          LibertyUpdateSpanAdvice.class.getName());
    }
  }
}
