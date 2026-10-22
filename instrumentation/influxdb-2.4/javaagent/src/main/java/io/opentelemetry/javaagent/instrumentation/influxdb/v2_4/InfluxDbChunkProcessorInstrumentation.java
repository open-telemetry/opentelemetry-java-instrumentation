/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

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
        "com.squareup.moshi.JsonUtf8Reader",
        "retrofit2.converter.moshi.MoshiResponseBodyConverter",
        "okio.RealBufferedSource",
        "okio.Buffer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("process"), getClass().getName() + "$ProcessAdvice");
    transformer.applyAdviceToMethod(
        named("fromJson").and(takesArgument(0, named("okio.BufferedSource"))),
        getClass().getName() + "$ReadAdvice");
    transformer.applyAdviceToMethod(
        named("convert").and(takesArgument(0, named("okhttp3.ResponseBody"))),
        getClass().getName() + "$ReadAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("beginArray", "beginObject"), getClass().getName() + "$JsonReadAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("nextNonWhitespace", "readEscapeCharacter"),
        getClass().getName() + "$JsonEofAdvice");
    transformer.applyAdviceToMethod(
        named("request").and(takesArguments(long.class)), getClass().getName() + "$RequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class JsonEofAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Scope onEnter(@Advice.FieldValue("source") Object source) {
      Context current = Context.current();
      InfluxDbQueryState.ResponseDecodeState decodeState =
          current.get(InfluxDbQueryState.RESPONSE_DECODE_STATE);
      if (decodeState == null) {
        return null;
      }
      return current
          .with(
              InfluxDbQueryState.RESPONSE_DECODE_STATE,
              new InfluxDbQueryState.ResponseDecodeState(source, decodeState.valueStarted()))
          .makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable Scope scope, @Advice.Thrown @Nullable Throwable error) {
      if (scope == null) {
        return;
      }
      Context current = Context.current();
      InfluxDbQueryState state = current.get(InfluxDbQueryState.RESPONSE_PROCESSING_STATE);
      InfluxDbQueryState.ResponseDecodeState decodeState =
          current.get(InfluxDbQueryState.RESPONSE_DECODE_STATE);
      scope.close();
      // Moshi creates EOFException after request() returns false, not when source I/O throws.
      if (state != null
          && error instanceof EOFException
          && decodeState != null
          && decodeState.incompleteValue()) {
        state.onDecodeError(error);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object source,
        @Advice.Return boolean available,
        @Advice.Thrown @Nullable Throwable error) {
      InfluxDbQueryState.ResponseDecodeState decodeState =
          Context.current().get(InfluxDbQueryState.RESPONSE_DECODE_STATE);
      if (decodeState != null) {
        decodeState.onRequest(source, error == null && !available);
      }
    }
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
