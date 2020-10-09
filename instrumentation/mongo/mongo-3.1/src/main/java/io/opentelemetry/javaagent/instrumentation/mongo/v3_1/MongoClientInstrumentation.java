/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.mongo.v3_1;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import com.mongodb.MongoClientOptions;
import com.mongodb.event.CommandListener;
import io.opentelemetry.instrumentation.auto.mongo.TracingCommandListener;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class MongoClientInstrumentation extends Instrumenter.Default {

  public MongoClientInstrumentation() {
    super("mongo");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.mongodb.MongoClientOptions$Builder")
        .and(
            declaresMethod(
                named("addCommandListener")
                    .and(
                        takesArguments(
                            new TypeDescription.Latent(
                                "com.mongodb.event.CommandListener",
                                Modifier.PUBLIC,
                                null,
                                Collections.<TypeDescription.Generic>emptyList())))
                    .and(isPublic())));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.auto.mongo.MongoClientTracer",
      "io.opentelemetry.instrumentation.auto.mongo.TracingCommandListener"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(named("build")).and(takesArguments(0)),
        MongoClientInstrumentation.class.getName() + "$MongoClientAdvice");
  }

  public static class MongoClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void injectTraceListener(
        @Advice.This MongoClientOptions.Builder builder,
        @Advice.FieldValue("commandListeners") List<CommandListener> commandListeners) {
      for (CommandListener commandListener : commandListeners) {
        if (commandListener instanceof TracingCommandListener) {
          return;
        }
      }
      builder.addCommandListener(new TracingCommandListener());
    }
  }
}
