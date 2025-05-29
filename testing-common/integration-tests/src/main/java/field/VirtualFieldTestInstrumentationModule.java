/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package field;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class VirtualFieldTestInstrumentationModule extends InstrumentationModule {
  public VirtualFieldTestInstrumentationModule() {
    super("virtual-field-test");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new TestInstrumentation());
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("field.VirtualFieldTestHelper");
  }

  private static class TestInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("field.VirtualFieldTest");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          named("virtualFieldTestMethod"),
          VirtualFieldTestInstrumentationModule.class.getName() + "$TestAdvice");
    }
  }

  @SuppressWarnings("unused")
  public static class TestAdvice {
    @Advice.OnMethodExit
    public static void onExit(@Advice.Return(readOnly = false) boolean result) {
      VirtualFieldTestHelper.test();
      result = true;
    }
  }
}
