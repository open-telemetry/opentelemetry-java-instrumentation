/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.weblogic;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Adds an instrumentation to collect middleware attributes for WebLogic Server 12 and 14. As span
 * detection on WebLogic does not require any special logic, this does not initiate servlet spans by
 * itself, but saves the special attributes as a map to a servlet request attribute, which is then
 * later read when span is started by generic servlet instrumentation.
 */
@AutoService(InstrumentationModule.class)
public class WebLogicInstrumentationModule extends InstrumentationModule {
  public WebLogicInstrumentationModule() {
    super("weblogic");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new MiddlewareInstrumentation());
  }

  private static class MiddlewareInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return named("weblogic.servlet.internal.WebAppServletContext$ServletInvocationAction");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          named("wrapRun")
              .and(takesArgument(1, named("javax.servlet.http.HttpServletRequest")))
              .and(isPrivate()),
          WebLogicInstrumentationModule.class.getPackage().getName() + ".WebLogicMiddlewareAdvice");
    }
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".WebLogicMiddlewareAdvice",
      packageName + ".WebLogicEntity",
      packageName + ".WebLogicEntity$Request",
      packageName + ".WebLogicEntity$Context",
      packageName + ".WebLogicEntity$Server",
      packageName + ".WebLogicEntity$Bean"
    };
  }
}
