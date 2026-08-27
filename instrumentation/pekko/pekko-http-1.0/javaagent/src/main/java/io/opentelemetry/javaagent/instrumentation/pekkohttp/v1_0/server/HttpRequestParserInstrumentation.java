/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.http.impl.engine.parsing.HttpMessageParser;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;
import org.apache.pekko.http.scaladsl.model.HttpMethod;
import org.apache.pekko.http.scaladsl.model.Uri;
import org.apache.pekko.util.ByteString;

/**
 * Recovers the method and the request target for a request that fails to parse. The parser discards
 * both when it turns the failure into a {@code ParserOutput.MessageStartError}, which carries only
 * a status and an {@code ErrorInfo}, so by the time the parsing error handler runs nothing
 * describes the request any more.
 */
class HttpRequestParserInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.pekko.http.impl.engine.parsing.HttpRequestParser");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // the parser logic is an anonymous class, so it is matched by what it declares rather than by
    // name; requiring the fields also makes the matcher its own guard, because if they are renamed
    // or removed the type stops matching and the span is emitted without them rather than with
    // something wrong
    return hasSuperType(named("org.apache.pekko.http.impl.engine.parsing.HttpMessageParser"))
        .and(declaresField(named("method")))
        .and(declaresField(named("uri")))
        .and(declaresField(named("uriBytes")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("parseMessage").and(takesArguments(2)), getClass().getName() + "$ParseMessageAdvice");
    transformer.applyAdviceToMethod(
        named("parseRequestTarget").and(takesArguments(2)),
        getClass().getName() + "$ParseRequestTargetAdvice");
    transformer.applyAdviceToMethod(
        named("failMessageStart")
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.pekko.http.scaladsl.model.StatusCode")))
            .and(takesArgument(1, named("org.apache.pekko.http.scaladsl.model.ErrorInfo"))),
        getClass().getName() + "$FailMessageStartAdvice");
  }

  @SuppressWarnings("unused")
  public static class ParseMessageAdvice {

    /**
     * The parser is reused for every request on a keep alive connection and never resets the fields
     * the request line is read from, so what an earlier request left behind is discarded when the
     * next one starts.
     */
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.This HttpMessageParser<?> parser) {
      PekkoHttpParsingErrorSingletons.startRequest(parser);
    }
  }

  @SuppressWarnings("unused")
  public static class ParseRequestTargetAdvice {

    /**
     * On a normal return the target is the one pekko-http parsed and validated. On a throw it is
     * the target that failed, which is only available as the raw bytes, and the {@code uri} field
     * still holds whatever the previous request on this connection left there.
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This HttpMessageParser<?> parser,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.FieldValue("method") @Nullable HttpMethod method,
        @Advice.FieldValue("uri") @Nullable Uri uri,
        @Advice.FieldValue("uriBytes") @Nullable ByteString uriBytes) {
      if (throwable == null) {
        PekkoHttpParsingErrorSingletons.captureParsedRequestLine(parser, method, uri);
      } else {
        PekkoHttpParsingErrorSingletons.captureUnparsedRequestLine(parser, method, uriBytes);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class FailMessageStartAdvice {

    /** Every parsing failure funnels through here, whichever token it happened on. */
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This HttpMessageParser<?> parser, @Advice.Argument(1) ErrorInfo info) {
      PekkoHttpParsingErrorSingletons.bindRequestLine(parser, info);
    }
  }
}
