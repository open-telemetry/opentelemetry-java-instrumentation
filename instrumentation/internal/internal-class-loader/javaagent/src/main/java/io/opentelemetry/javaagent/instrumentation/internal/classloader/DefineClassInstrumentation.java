/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DefineClassInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("java.lang.ClassLoader");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // getDefineClassTransformer
    // we must use inlined instrumentation with ASM to prevent stack overflow errors
    transformer.applyTransformer(ClassLoaderAsmUtil.getDefineClassTransformer());
  }
}
