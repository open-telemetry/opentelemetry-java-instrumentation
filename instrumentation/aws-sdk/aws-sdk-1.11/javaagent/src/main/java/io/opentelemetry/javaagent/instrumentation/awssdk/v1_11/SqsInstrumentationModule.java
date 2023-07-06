/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static net.bytebuddy.matcher.ElementMatchers.none;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.awssdk.v1_11.SqsAdviceBridge;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;

@AutoService(InstrumentationModule.class)
public class SqsInstrumentationModule extends AbstractAwsSdkInstrumentationModule {

  public SqsInstrumentationModule() {
    super("aws-sdk-1.11-sqs");
  }

  @Override
  public void doTransform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        none(), SqsInstrumentationModule.class.getName() + "$RegisterAdvice");
  }

  @SuppressWarnings("unused")
  public static class RegisterAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit() {
      // (indirectly) using SqsImpl class here to make sure it is available from SqsAccess
      // (injected into app classloader) and checked by Muzzle
      SqsAdviceBridge.referenceForMuzzleOnly();
    }
  }
}
