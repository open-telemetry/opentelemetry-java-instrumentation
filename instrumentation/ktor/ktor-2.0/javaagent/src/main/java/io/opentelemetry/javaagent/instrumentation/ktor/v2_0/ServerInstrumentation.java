/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ktor.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.ktor.server.application.Application;
import io.ktor.server.application.ApplicationPluginKt;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.ktor.v2_0.internal.KtorBuilderUtil;
import io.opentelemetry.instrumentation.ktor.v2_0.server.KtorServerTracingBuilder;
import io.opentelemetry.instrumentation.ktor.v2_0.server.KtorServerTracingKt;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ServerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "io.ktor.server.engine.ApplicationEngineEnvironmentReloading", // Ktor 2.0
        "io.ktor.server.engine.EmbeddedServer" // Ktor 3.0
        );
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), this.getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit
    public static void onExit(@Advice.FieldValue("_applicationInstance") Application application) {
      ApplicationPluginKt.install(application, KtorServerTracingKt.getKtorServerTracing(), new SetupFunction());
    }
  }

  public static class SetupFunction
      implements Function1<KtorServerTracingBuilder, kotlin.Unit> {

    @Override
    public Unit invoke(KtorServerTracingBuilder configuration) {
      configuration.setOpenTelemetry(GlobalOpenTelemetry.get());
      KtorBuilderUtil.serverBuilderExtractor
          .invoke(configuration)
          .configure(AgentCommonConfig.get());
      return kotlin.Unit.INSTANCE;
    }
  }
}
