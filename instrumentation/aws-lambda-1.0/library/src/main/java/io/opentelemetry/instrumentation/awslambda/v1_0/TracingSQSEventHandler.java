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

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.concurrent.TimeUnit;

public abstract class TracingSQSEventHandler implements RequestHandler<SQSEvent, Void> {

  private final AwsLambdaMessageTracer tracer;

  /** Creates a new {@link TracingRequestHandler} which traces using the default {@link Tracer}. */
  protected TracingSQSEventHandler() {
    this.tracer = new AwsLambdaMessageTracer();
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link Tracer}.
   */
  protected TracingSQSEventHandler(Tracer tracer) {
    this.tracer = new AwsLambdaMessageTracer(tracer);
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link
   * AwsLambdaMessageTracer}.
   */
  protected TracingSQSEventHandler(AwsLambdaMessageTracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public Void handleRequest(SQSEvent event, Context context) {
    Span span = tracer.startSpan(context, event);
    Throwable error = null;
    try (Scope ignored = tracer.startScope(span)) {
      handleEvent(event, context);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      if (error != null) {
        tracer.endExceptionally(span, error);
      } else {
        tracer.end(span);
      }
      OpenTelemetrySdk.getTracerProvider().forceFlush().join(1, TimeUnit.SECONDS);
    }
    return null;
  }

  protected abstract void handleEvent(SQSEvent event, Context context);

  // We use in SQS message handler too.
  AwsLambdaMessageTracer getTracer() {
    return tracer;
  }
}
