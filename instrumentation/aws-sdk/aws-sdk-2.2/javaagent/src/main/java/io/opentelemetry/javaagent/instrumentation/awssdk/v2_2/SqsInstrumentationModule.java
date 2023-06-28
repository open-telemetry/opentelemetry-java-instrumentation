/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.awssdk.v2_2.SqsAdviceBridge;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SqsInstrumentationModule extends AbstractAwsSdkInstrumentationModule {

  public SqsInstrumentationModule() {
    super("aws-sdk-2.2-sqs");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new DefaultSqsClientTypeInstrumentation());
  }

  public static class DefaultSqsClientTypeInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("software.amazon.awssdk.services.sqs.DefaultSqsClient");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          isConstructor(), SqsInstrumentationModule.class.getName() + "$RegisterAdvice");
    }
  }

  @SuppressWarnings("unused")
  public static class RegisterAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit() {
      // (indirectly) using SqsImpl class here to make sure it is available from SqsAccess
      // (injected into app classloader) and checked by Muzzle
      SqsAdviceBridge.init();
    }
  }
}
