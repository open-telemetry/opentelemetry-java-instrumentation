/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.grizzly;

import static io.opentelemetry.auto.instrumentation.grizzly.GrizzlyHttpServerTracer.TRACER;

import io.grpc.Context;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpPacketParsing;
import org.glassfish.grizzly.http.HttpRequestPacket;

public class HttpCodecFilterOldAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(
      @Advice.Origin final Method method,
      @Advice.Argument(0) final FilterChainContext ctx,
      @Advice.Argument(1) final HttpPacketParsing httpHeader) {
    Context context = TRACER.getServerSpanContext(ctx);

    // only create a span if there isn't another one attached to the current ctx
    // and if the httpHeader has been parsed into a HttpRequestPacket
    if (context != null || !(httpHeader instanceof HttpRequestPacket)) {
      return;
    }
    final HttpRequestPacket httpRequest = (HttpRequestPacket) httpHeader;
    Span span = TRACER.startSpan(httpRequest, httpRequest, method, null);

    TRACER.startScope(span, ctx);
  }
}
