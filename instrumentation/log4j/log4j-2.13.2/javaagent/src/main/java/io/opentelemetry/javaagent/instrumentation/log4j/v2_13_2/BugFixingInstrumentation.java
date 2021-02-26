/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.v2_13_2;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.logging.log4j.core.config.Property;

// Log4 J 2.13.2 has a critical bug that prevents using ContextDataProvider with many log4j.xml
// configurations. We introduce a patch which should be low enough overhead to keep on higher
// versions too.
//
// https://github.com/apache/logging-log4j2/commit/e5394028c000008505991c45b8ce593422f7ac55
public class BugFixingInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named(
        "org.apache.logging.log4j.core.impl.ThreadContextDataInjector$ForCopyOnWriteThreadContextMap");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("injectContextData"))
            .and(takesArgument(0, List.class)),
        BugFixingInstrumentation.class.getName() + "$BugFixingAdvice");
  }

  public static class BugFixingAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 0, readOnly = false) List<Property> props) {
      if (props == null) {
        props = Collections.emptyList();
      }
    }
  }
}
