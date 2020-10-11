/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.log4j.v2_7;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.logging.log4j.core.ContextDataInjector;

@AutoService(Instrumenter.class)
public class Log4j27MdcInstrumentation extends Instrumenter.Default {
  public Log4j27MdcInstrumentation() {
    super("log4j2", "log4j", "log4j-2.7");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.auto.log4j.v2_7.SpanDecoratingContextDataInjector"
    };
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.apache.logging.log4j.core.impl.ContextDataInjectorFactory")
        .and(not(hasClassesNamed("org.apache.logging.log4j.core.util.ContextDataProvider")));
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.apache.logging.log4j.core.impl.ContextDataInjectorFactory");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod()
            .and(isPublic())
            .and(isStatic())
            .and(named("createInjector"))
            .and(returns(named("org.apache.logging.log4j.core.ContextDataInjector"))),
        Log4j27MdcInstrumentation.class.getName() + "$CreateInjectorAdvice");
  }

  public static class CreateInjectorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Return(typing = Typing.DYNAMIC, readOnly = false) ContextDataInjector injector) {
      injector = new SpanDecoratingContextDataInjector(injector);
    }
  }
}
