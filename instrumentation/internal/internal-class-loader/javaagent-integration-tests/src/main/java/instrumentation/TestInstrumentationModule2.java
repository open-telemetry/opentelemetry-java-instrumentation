/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package instrumentation;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class TestInstrumentationModule2 extends InstrumentationModule {
  public TestInstrumentationModule2() {
    super("test-instrumentation2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new TestTypeInstrumentation());
  }

  @Override
  public boolean isHelperClass(String className) {
    return "instrumentation.TestFailableCallable".equals(className);
  }

  public static class TestTypeInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("org.apache.commons.lang3.function.FailableCallable");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return implementsInterface(named("org.apache.commons.lang3.function.FailableCallable"));
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          none(), TestInstrumentationModule2.class.getName() + "$TestAdvice");
    }
  }

  @SuppressWarnings("unused")
  public static class TestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static String onEnter() {
      return TestFailableCallable.class.getName();
    }
  }
}
