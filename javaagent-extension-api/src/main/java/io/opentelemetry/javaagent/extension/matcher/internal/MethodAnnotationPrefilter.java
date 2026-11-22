/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher.internal;

import static java.nio.charset.StandardCharsets.US_ASCII;

import javax.annotation.Nullable;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Prefilter for method annotations. A positive result still requires the ordinary matcher.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MethodAnnotationPrefilter
    extends ElementMatcher.Junction.AbstractBase<TypeDescription> {

  @Nullable private final byte[] descriptor;

  public MethodAnnotationPrefilter(String annotationName) {
    String name = "L" + annotationName.replace('.', '/') + ";";
    descriptor = US_ASCII.newEncoder().canEncode(name) ? name.getBytes(US_ASCII) : null;
  }

  @Override
  public boolean matches(TypeDescription target) {
    return descriptor == null
        || !(target instanceof ClassFileBacked)
        || ((ClassFileBacked) target).mayDeclareMethodAnnotation(this);
  }

  public boolean mayBePresentIn(byte[] classFile) {
    if (descriptor == null) {
      return true;
    }
    // A method annotation's descriptor must occur in the constant pool.
    search:
    for (int start = 0; start <= classFile.length - descriptor.length; start++) {
      for (int i = 0; i < descriptor.length; i++) {
        if (classFile[start + i] != descriptor[i]) {
          continue search;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * A type description that can prove an annotation is absent from its class file.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public interface ClassFileBacked {
    boolean mayDeclareMethodAnnotation(MethodAnnotationPrefilter prefilter);
  }
}
