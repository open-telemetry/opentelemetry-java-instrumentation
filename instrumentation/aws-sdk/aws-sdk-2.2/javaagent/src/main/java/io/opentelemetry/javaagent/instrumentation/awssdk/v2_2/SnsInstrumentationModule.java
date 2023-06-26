/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import static net.bytebuddy.matcher.ElementMatchers.none;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.awssdk.v2_2.SnsAdviceBridge;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;

@AutoService(InstrumentationModule.class)
public class SnsInstrumentationModule extends AbstractAwsSdkInstrumentationModule {

  public SnsInstrumentationModule() {
    super("aws-sdk-2.2-sns");
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
