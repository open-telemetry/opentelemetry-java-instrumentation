/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ReactorInstrumentationModule extends InstrumentationModule {

  public ReactorInstrumentationModule() {
    super("reactor", "reactor-3.1");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HooksInstrumentation());
  }

  public static class HooksInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("reactor.core.publisher.Hooks");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          isTypeInitializer().or(named("resetOnEachOperator")), ReactorHooksAdvice.class.getName());
    }
  }
}
