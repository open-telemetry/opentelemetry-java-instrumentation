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

import akka.NotUsed;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EchoServiceImpl implements EchoService {
  private static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto");

  @Override
  public ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> echo() {
    CompletableFuture<Source<String, NotUsed>> fut = new CompletableFuture<>();
    ServiceTestModule.executor.submit(() -> fut.complete(Source.from(tracedMethod())));
    return req -> fut;
  }

  @Override
  public ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> error() {
    throw new RuntimeException("lagom exception");
  }

  public List<String> tracedMethod() {
    Span span = TRACER.spanBuilder("tracedMethod").startSpan();
    try {
      return java.util.Arrays.asList("msg1", "msg2", "msg3");
    } finally {
      span.end();
    }
  }
}
