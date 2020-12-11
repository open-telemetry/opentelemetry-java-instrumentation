/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.khttp;

import static io.opentelemetry.javaagent.instrumentation.khttp.KHttpHeadersInjectAdapter.asWritable;
import static io.opentelemetry.javaagent.instrumentation.khttp.KHttpTracer.tracer;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.Operation;
import java.util.Map;
import khttp.responses.Response;
import net.bytebuddy.asm.Advice;

public class KHttpAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void methodEnter(
      @Advice.Argument(value = 0) String method,
      @Advice.Argument(value = 1) String uri,
      @Advice.Argument(value = 2, readOnly = false) Map<String, String> headers,
      @Advice.Local("otelOperation") Operation<Response> operation,
      @Advice.Local("otelScope") Scope scope) {
    headers = asWritable(headers);
    operation = tracer().startOperation(new RequestWrapper(method, uri, headers), headers);
    scope = operation.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Return Response response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelOperation") Operation<Response> operation,
      @Advice.Local("otelScope") Scope scope) {
    scope.close();
    operation.endMaybeExceptionally(response, throwable);
  }
}
