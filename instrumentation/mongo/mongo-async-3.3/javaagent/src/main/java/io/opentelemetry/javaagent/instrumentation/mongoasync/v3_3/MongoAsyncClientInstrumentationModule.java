/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongoasync.v3_3;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.event.CommandListener;
import io.opentelemetry.javaagent.instrumentation.mongo.TracingCommandListener;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class MongoAsyncClientInstrumentationModule extends InstrumentationModule {

  public MongoAsyncClientInstrumentationModule() {
    super("mongo-async", "mongo-async-3.3", "mongo");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new MongoClientSettingsBuildersInstrumentation());
  }

  private static final class MongoClientSettingsBuildersInstrumentation
      implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.mongodb.async.client.MongoClientSettings$Builder")
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
                      .and(isPublic())))
          .and(declaresField(named("commandListeners")));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod().and(isPublic()).and(named("build")).and(takesArguments(0)),
          MongoAsyncClientInstrumentationModule.class.getName() + "$MongoAsyncClientAdvice");
    }
  }

  public static class MongoAsyncClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void injectTraceListener(
        @Advice.This MongoClientSettings.Builder builder,
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
