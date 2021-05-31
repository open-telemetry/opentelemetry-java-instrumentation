/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.mongodb.MongoClientSettings;
import com.mongodb.event.CommandListener;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class MongoClientSettingsBuilderInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.mongodb.MongoClientSettings$Builder")
        .and(
            declaresMethod(
                named("addCommandListener")
                    .and(isPublic())
                    .and(
                        takesArguments(1)
                            .and(takesArgument(0, named("com.mongodb.event.CommandListener"))))));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("build")).and(takesArguments(0)),
        this.getClass().getName() + "$BuildAdvice");
  }

  public static class BuildAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void injectTraceListener(
        @Advice.This MongoClientSettings.Builder builder,
        @Advice.FieldValue("commandListeners") List<CommandListener> commandListeners) {
      for (CommandListener commandListener : commandListeners) {
        if (commandListener == MongoInstrumentationSingletons.LISTENER) {
          return;
        }
      }
      builder.addCommandListener(MongoInstrumentationSingletons.LISTENER);
    }
  }
}
