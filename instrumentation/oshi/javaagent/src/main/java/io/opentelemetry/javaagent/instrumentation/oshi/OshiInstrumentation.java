/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class OshiInstrumentation extends AbstractOshiInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("oshi.SystemInfo");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("oshi.SystemInfo");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod().and(isPublic()).and(isStatic()).and(named("getCurrentPlatformEnum")),
        OshiInstrumentation.class.getName() + "$OshiInstrumentationAdvice");
  }

  public static class OshiInstrumentationAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      OshiInstrumentationHelper.registerObservers();
    }
  }
}
