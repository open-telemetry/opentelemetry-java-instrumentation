/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.mongo.v3_1.Mongo31Singletons.tracingListener;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import com.mongodb.MongoClientOptions;
import com.mongodb.event.CommandListener;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class Mongo31InstrumentationModule extends InstrumentationModule {

  public Mongo31InstrumentationModule() {
    super(
        "mongo",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"mongo-3.1"}
            : new String[] {"mongo-3.1", "mongo-3.1-client-options"});
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // present in supported synchronous drivers and absent from the async driver
    return hasClassesNamed("com.mongodb.MongoClientOptions");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new MongoClientOptionsBuilderInstrumentation(),
        new Mongo31ClusterSettingsBuilderInstrumentation(),
        new MongoClientUriInstrumentation(),
        new Mongo31ClusterInstrumentation());
  }

  public static final class MongoClientOptionsBuilderInstrumentation
      implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.mongodb.MongoClientOptions$Builder")
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
          isPublic().and(named("build")).and(takesArguments(0)),
          getClass().getName() + "$MongoClientAdvice");
    }

    @SuppressWarnings("unused")
    public static class MongoClientAdvice {

      @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
      public static void injectTraceListener(
          @Advice.This MongoClientOptions.Builder builder,
          @Advice.FieldValue("commandListeners") List<CommandListener> commandListeners) {
        for (CommandListener commandListener : commandListeners) {
          if (Mongo31Singletons.isTracingListener(commandListener)) {
            return;
          }
        }
        builder.addCommandListener(tracingListener());
      }
    }
  }
}
