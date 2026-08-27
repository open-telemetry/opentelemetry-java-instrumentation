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
import org.apache.pekko.http.impl.engine.parsing.ParserOutput;
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
        named("emit")
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.apache.pekko.http.impl.engine.parsing.ParserOutput"))),
        getClass().getName() + "$EmitAdvice");
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

    /** Remembers the target of the request before this one, see the exit advice. */
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnter(@Advice.FieldValue("uriBytes") @Nullable ByteString uriBytes) {
      return uriBytes;
    }

    /**
     * On a normal return the target is the one pekko-http parsed and validated. On a throw it is
     * the target that failed, which is only available as the raw bytes, and the {@code uri} field
     * still holds whatever the previous request on this connection left there.
     *
     * <p>The raw bytes can be stale as well: pekko-http throws {@code UriTooLong} from the scan
     * that finds the end of the target, which runs before it assigns the field, so an overlong
     * target would otherwise be reported as the path of the request before it. The field is only
     * trusted when it changed during this call.
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This HttpMessageParser<?> parser,
        @Advice.Enter @Nullable Object previousUriBytes,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.FieldValue("method") @Nullable HttpMethod method,
        @Advice.FieldValue("uri") @Nullable Uri uri,
        @Advice.FieldValue("uriBytes") @Nullable ByteString uriBytes) {
      if (throwable == null) {
        PekkoHttpParsingErrorSingletons.captureParsedRequestLine(parser, method, uri);
      } else {
        PekkoHttpParsingErrorSingletons.captureUnparsedRequestLine(
            parser, method, uriBytes == previousUriBytes ? null : uriBytes);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class EmitAdvice {

    /**
     * Every parsing failure reaches the rest of the stack as a {@code MessageStartError} passed to
     * this method, whether it came from the parser giving up on a token or from the connection
     * ending while a message was still incomplete, which does not go through {@code
     * failMessageStart}.
     */
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This HttpMessageParser<?> parser, @Advice.Argument(0) ParserOutput output) {
      PekkoHttpParsingErrorSingletons.bindRequestLine(parser, output);
    }
  }
}
