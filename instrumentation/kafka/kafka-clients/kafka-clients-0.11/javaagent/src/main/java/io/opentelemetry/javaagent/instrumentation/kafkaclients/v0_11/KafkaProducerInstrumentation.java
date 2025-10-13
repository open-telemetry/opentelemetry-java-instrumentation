/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaSingletons.producerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProducerRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaPropagation;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.ApiVersions;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;

public class KafkaProducerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.clients.producer.KafkaProducer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("send"))
            .and(takesArgument(0, named("org.apache.kafka.clients.producer.ProducerRecord")))
            .and(takesArgument(1, named("org.apache.kafka.clients.producer.Callback"))),
        KafkaProducerInstrumentation.class.getName() + "$SendAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    public static class AdviceScope {
      private final KafkaProducerRequest request;
      private final Context context;
      private final Scope scope;
      private final Context parentContext;

      private AdviceScope(
          Context parentContext, KafkaProducerRequest request, Context context, Scope scope) {
        this.parentContext = parentContext;
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(KafkaProducerRequest request) {
        Context parentContext = Java8BytecodeBridge.currentContext();
        if (!producerInstrumenter().shouldStart(parentContext, request)) {
          return null;
        }
        Context context = producerInstrumenter().start(parentContext, request);
        return new AdviceScope(parentContext, request, context, context.makeCurrent());
      }

      public Callback wrapCallback(Callback originalCallback) {
        return new ProducerCallback(originalCallback, parentContext, context, request);
      }

      public ProducerRecord<?, ?> propagateContext(
          ApiVersions apiVersions, ProducerRecord<?, ?> record) {
        if (KafkaSingletons.isProducerPropagationEnabled()
            && KafkaPropagation.shouldPropagate(apiVersions)) {
          return KafkaPropagation.propagateContext(context, record);
        }
        return record;
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        if (throwable != null) {
          producerInstrumenter().end(context, request, null, throwable);
        }
        // span finished by ProducerCallback
      }
    }

    @AssignReturned.ToArguments({
      @ToArgument(value = 0, index = 1),
      @ToArgument(value = 1, index = 2)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] onEnter(
        @Advice.FieldValue("apiVersions") ApiVersions apiVersions,
        @Advice.FieldValue("clientId") String clientId,
        @Advice.Argument(0) ProducerRecord<?, ?> originalRecord,
        @Advice.Argument(1) Callback originalCallback) {
      ProducerRecord<?, ?> record = originalRecord;
      Callback callback = originalCallback;

      KafkaProducerRequest request = KafkaProducerRequest.create(record, clientId);
      AdviceScope adviceScope = AdviceScope.start(request);
      if (adviceScope == null) {
        return new Object[] {null, record, callback};
      }
      record = adviceScope.propagateContext(apiVersions, record);
      callback = adviceScope.wrapCallback(callback);
      return new Object[] {adviceScope, record, callback};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter Object[] enterResult) {

      AdviceScope adviceScope = (AdviceScope) enterResult[0];
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
