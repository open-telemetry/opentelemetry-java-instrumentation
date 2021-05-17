/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package instrumentation;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TestTypeInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.commons.lang3.SystemUtils");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.commons.lang3.SystemUtils");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getHostName"), TestTypeInstrumentation.class.getName() + "$GetHostNameAdvice");
  }

  public static class GetHostNameAdvice {
    @Advice.OnMethodExit
    public static void methodExit(@Advice.Return(readOnly = false) String hostName) {
      hostName = "not-the-host-name";
    }
  }
}
