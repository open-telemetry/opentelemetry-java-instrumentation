/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class IbmResourceLevelInstrumentation extends InstrumentationModule {
  public IbmResourceLevelInstrumentation() {
    super(IbmResourceLevelInstrumentation.class.getName());
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ResourceLevelInstrumentation());
  }

  public static class ResourceLevelInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.ibm.as400.resource.ResourceLevel");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(named("toString"), ToStringAdvice.class.getName());
    }
  }

  public static class ToStringAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    static void toStringReplace(@Advice.Return(readOnly = false) String ret) {
      ret = "instrumented";
    }
  }
}
