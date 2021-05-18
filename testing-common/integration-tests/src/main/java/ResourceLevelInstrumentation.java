/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ResourceLevelInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.ibm.as400.resource.ResourceLevel");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("toString"), this.getClass().getName() + "$ToStringAdvice");
  }

  public static class ToStringAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    static void toStringReplace(@Advice.Return(readOnly = false) String ret) {
      ret = "instrumented";
    }
  }
}
