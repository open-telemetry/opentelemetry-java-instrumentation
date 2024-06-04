/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.nativesupport;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import io.opentelemetry.javaagent.instrumentation.internal.reflection.ReflectionHelper;

/**
 * This class is the native support version of {@link
 * io.opentelemetry.javaagent.instrumentation.internal.reflection.ClassInstrumentation}. <br>
 * The {@code ClassInstrumentation} class transforms {@link Class} at Java program
 * <b>runtime</b>. This class do the same transform at native image <b>build time</b>.
 */
@TargetClass(Class.class)
public final class Target_java_lang_Class {

  @Alias(noSubstitution = true)
  @TargetElement(name = "getInterfaces")
  private native Class<?>[] originalGetInterfaces(boolean clone);

  /**
   * This substituted method filters the result of original method. <br>
   * {@code (Class<?>)(Object)this} makes javac happy. At javac compile time, {@code this} is {@code
   * Target_java_lang_Class}. At runtime, {@code this} is the original class, i.e. {@code
   * java.lang.Class}.
   */
  @Substitute
  private Class<?>[] getInterfaces(boolean clone) {
    return ReflectionHelper.filterInterfaces(
        originalGetInterfaces(clone), (Class<?>) (Object) this);
  }
}
