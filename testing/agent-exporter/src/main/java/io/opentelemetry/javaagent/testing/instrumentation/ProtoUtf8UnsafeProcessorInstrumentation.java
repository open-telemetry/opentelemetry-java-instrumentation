/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ProtoUtf8UnsafeProcessorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.opentelemetry.testing.internal.protobuf.Utf8$UnsafeProcessor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("isAvailable"), this.getClass().getName() + "$IsAvailableAdvice");
  }

  @SuppressWarnings("unused")
  public static class IsAvailableAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
    public static boolean onEnter() {
      return true;
    }

    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static boolean onExit() {
      // make isAvailable always return false
      return false;
    }
  }
}
