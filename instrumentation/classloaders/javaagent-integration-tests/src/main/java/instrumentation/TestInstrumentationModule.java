/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package instrumentation;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class TestInstrumentationModule extends InstrumentationModule {
  public TestInstrumentationModule() {
    super("test-instrumentation");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new TestTypeInstrumentation());
  }

  public static class TestTypeInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("org.apache.commons.lang3.SystemUtils");
    }

    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return named("org.apache.commons.lang3.SystemUtils");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return Collections.singletonMap(named("getHostName"), MarkInstrumentedAdvice.class.getName());
    }
  }

  public static class MarkInstrumentedAdvice {
    @Advice.OnMethodExit
    public static void methodExit(@Advice.Return(readOnly = false) String hostName) {
      hostName = "not-the-host-name";
    }
  }
}
