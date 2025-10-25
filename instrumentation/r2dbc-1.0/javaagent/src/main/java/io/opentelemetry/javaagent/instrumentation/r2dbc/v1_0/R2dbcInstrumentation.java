/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class R2dbcInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.r2dbc.spi.ConnectionFactories");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("find"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.r2dbc.spi.ConnectionFactoryOptions"))),
        this.getClass().getName() + "$FactoryAdvice");
  }

  @SuppressWarnings("unused")
  public static class FactoryAdvice {

    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static ConnectionFactory methodExit(
        @Advice.Return @Nullable ConnectionFactory factory,
        @Advice.Argument(0) ConnectionFactoryOptions factoryOptions) {

      if (factory == null) {
        return null;
      }

      return R2dbcSingletons.telemetry().wrapConnectionFactory(factory, factoryOptions);
    }
  }
}
