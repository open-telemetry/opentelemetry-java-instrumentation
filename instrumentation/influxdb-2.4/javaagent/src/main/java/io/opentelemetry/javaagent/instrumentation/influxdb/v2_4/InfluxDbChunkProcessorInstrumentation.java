/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.EOFException;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class InfluxDbChunkProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.influxdb.impl.InfluxDBImpl$JSONChunkProccesor",
        "org.influxdb.impl.InfluxDBImpl$MessagePackChunkProccesor",
        "com.squareup.moshi.JsonAdapter",
        "com.squareup.moshi.JsonUtf8Reader");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("process"), getClass().getName() + "$ProcessAdvice");
    transformer.applyAdviceToMethod(
        named("fromJson").and(takesArgument(0, named("okio.BufferedSource"))),
        getClass().getName() + "$ReadAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("beginArray", "beginObject"), getClass().getName() + "$JsonReadAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcessAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Thrown @Nullable Throwable error) {
      InfluxDbQueryState state = Context.current().get(InfluxDbQueryState.KEY);
      if (state != null && error != null) {
        state.onFailure(error);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ReadAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Scope onEnter() {
      Context current = Context.current();
      if (current.get(InfluxDbQueryState.RESPONSE_PROCESSING_STATE) == null) {
        return null;
      }
      return current
          .with(
              InfluxDbQueryState.RESPONSE_DECODE_STATE,
              new InfluxDbQueryState.ResponseDecodeState())
          .makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable Scope scope, @Advice.Thrown @Nullable Throwable error) {
      if (scope == null) {
        return;
      }
      InfluxDbQueryState state =
          Context.current().get(InfluxDbQueryState.RESPONSE_PROCESSING_STATE);
      InfluxDbQueryState.ResponseDecodeState decodeState =
          Context.current().get(InfluxDbQueryState.RESPONSE_DECODE_STATE);
      scope.close();
      if (state != null
          && error != null
          && (!(error instanceof EOFException)
              || (decodeState != null && decodeState.valueStarted()))) {
        state.onFailure(error);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class JsonReadAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit() {
      InfluxDbQueryState.ResponseDecodeState decodeState =
          Context.current().get(InfluxDbQueryState.RESPONSE_DECODE_STATE);
      if (decodeState != null) {
        decodeState.markValueStarted();
      }
    }
  }
}
