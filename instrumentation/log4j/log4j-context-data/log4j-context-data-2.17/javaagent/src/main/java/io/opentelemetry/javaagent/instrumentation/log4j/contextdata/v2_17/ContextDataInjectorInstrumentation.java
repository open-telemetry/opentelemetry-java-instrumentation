/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.contextdata.v2_17;

import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.fieldType;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.log4j.contextdata.v2_17.OpenTelemetryContextDataProvider;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.logging.log4j.core.util.ContextDataProvider;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;

class ContextDataInjectorInstrumentation implements TypeInstrumentation {
  private static final String INJECTOR =
      "org.apache.logging.log4j.core.impl.ThreadContextDataInjector$";

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(INJECTOR + "ForDefaultThreadContextMap")
        .or(named(INJECTOR + "ForCopyOnWriteThreadContextMap"))
        .or(named(INJECTOR + "ForGarbageFreeThreadContextMap"))
        .and(hasSuperType(declaresField(named("providers").and(fieldType(List.class)))));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("injectContextData")
            .and(takesArguments(2))
            .and(takesArgument(0, List.class))
            .and(takesArgument(1, named("org.apache.logging.log4j.util.StringMap")))
            .and(returns(named("org.apache.logging.log4j.util.StringMap"))),
        getClass().getName() + "$InjectContextDataAdvice");
  }

  @SuppressWarnings("unused")
  public static class InjectContextDataAdvice {
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static StringMap onExit(
        @Advice.FieldValue("providers") List<ContextDataProvider> providers,
        @Advice.Return StringMap contextData) {
      for (int i = 0; i < providers.size(); i++) {
        if (providers.get(i) instanceof OpenTelemetryContextDataProvider) {
          return contextData;
        }
      }

      // The injector can cache its providers before the agent adds the SPI resource.
      StringMap enriched = contextData;
      for (Map.Entry<String, String> entry :
          FallbackProvider.INSTANCE.supplyContextData().entrySet()) {
        if (!contextData.containsKey(entry.getKey())) {
          if (enriched == contextData) {
            enriched = new SortedArrayStringMap(contextData);
          }
          enriched.putValue(entry.getKey(), entry.getValue());
        }
      }
      if (enriched != contextData && contextData.isFrozen()) {
        enriched.freeze();
      }
      return enriched;
    }
  }

  private static class FallbackProvider {
    private static final ContextDataProvider INSTANCE = new OpenTelemetryContextDataProvider();
  }
}
