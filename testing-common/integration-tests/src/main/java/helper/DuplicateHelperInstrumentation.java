/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package helper;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DuplicateHelperInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("helper.DuplicateHelperTestClass");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("transform"), this.getClass().getName() + "$TestAdvice");
  }

  @SuppressWarnings("unused")
  public static class TestAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addSuffix(@Advice.Return(readOnly = false) String string) {
      string = DuplicateHelper.addSuffix(string, " foo");
    }
  }
}
