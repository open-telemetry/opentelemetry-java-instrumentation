/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.eclipse.osgi.v3_6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.Test;

class EclipseOsgiInstrumentationTest {

  @Test
  void matchesOnlyCompatibleBindings() {
    List<ElementMatcher<? super MethodDescription>> matchers = new ArrayList<>();
    TypeTransformer transformer = mock(TypeTransformer.class);
    doAnswer(
            invocation -> {
              matchers.add(invocation.getArgument(0));
              return null;
            })
        .when(transformer)
        .applyAdviceToMethod(any(), anyString());

    new EclipseOsgiInstrumentation().transform(transformer);

    assertThat(matchers).hasSize(1);
    ElementMatcher<? super MethodDescription> matcher = matchers.get(0);
    for (MethodDescription method :
        new TypeDescription.ForLoadedType(Signatures.class).getDeclaredMethods()) {
      if (!method.isMethod()) {
        continue;
      }
      boolean compatible =
          !method.getParameters().isEmpty()
              && method.getParameters().get(0).getType().asErasure().represents(String.class)
              && method.getReturnType().asErasure().represents(boolean.class);
      assertThat(matcher.matches(method)).isEqualTo(compatible);
    }
  }

  @SuppressWarnings("unused")
  static class Signatures {
    boolean isDynamicallyImported(String packageName) {
      return false;
    }

    boolean isDynamicallyImported(Object packageName) {
      return false;
    }

    boolean isDynamicallyImported(String packageName, boolean other) {
      return false;
    }

    boolean isDynamicallyImported() {
      return false;
    }

    Object isDynamicallyImported(String packageName, int other) {
      return null;
    }
  }
}
