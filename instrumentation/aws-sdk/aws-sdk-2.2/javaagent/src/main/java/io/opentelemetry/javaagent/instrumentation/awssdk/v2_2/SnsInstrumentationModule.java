/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.none;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SnsAdviceBridge;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SnsInstrumentationModule extends AbstractAwsSdkInstrumentationModule {

  public SnsInstrumentationModule() {
    super("aws-sdk-2.2-sns");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("software.amazon.awssdk.services.sns.SnsClient");
  }

  @Override
  public void doTransform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        none(), SnsInstrumentationModule.class.getName() + "$RegisterAdvice");
  }

  @SuppressWarnings("unused")
  public static class RegisterAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit() {
      // (indirectly) using SnsImpl class here to make sure it is available from SnsAccess
      // (injected into app classloader) and checked by Muzzle
      SnsAdviceBridge.referenceForMuzzleOnly();
    }
  }
}
