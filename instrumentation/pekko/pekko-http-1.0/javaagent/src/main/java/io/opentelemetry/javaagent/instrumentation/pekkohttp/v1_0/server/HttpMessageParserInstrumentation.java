/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.http.impl.engine.parsing.HttpMessageParser;
import org.apache.pekko.http.impl.engine.parsing.ParserOutput;

/**
 * Hands the request line that {@link HttpRequestParserInstrumentation} recovered to the parser
 * output that carries the failure, which is the last point where the parser and the failure are
 * both in reach.
 *
 * <p>{@code emit} is a method of the {@code HttpMessageParser} trait rather than of the parser
 * itself, and it can not be instrumented on the parser: scala 3 compiles the forwarders a class
 * inherits from a trait as bridge methods, and bridge methods are not advised, so an advice on the
 * parser's own {@code emit} silently does nothing when pekko-http is built for scala 3. Every scala
 * version puts the body of a concrete trait method in a static method named after it with a {@code
 * $} appended, and both the forwarder and the trait itself call that, so it is the one place all
 * the callers meet.
 */
class HttpMessageParserInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.http.impl.engine.parsing.HttpMessageParser");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("emit$")
            .and(takesArguments(2))
            .and(takesArgument(1, named("org.apache.pekko.http.impl.engine.parsing.ParserOutput"))),
        getClass().getName() + "$EmitAdvice");
  }

  @SuppressWarnings("unused")
  public static class EmitAdvice {

    /**
     * Every parsing failure reaches the rest of the stack as a {@code MessageStartError} passed to
     * this method, whether it came from the parser giving up on a token or from the connection
     * ending while a message was still incomplete.
     */
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.Argument(0) HttpMessageParser<?> parser, @Advice.Argument(1) ParserOutput output) {
      PekkoHttpParsingErrorSingletons.bindRequestLine(parser, output);
    }
  }
}
