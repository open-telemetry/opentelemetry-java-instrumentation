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
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public abstract class TracingSQSMessageHandler extends TracingSQSEventHandler {

  /** Creates a new {@link TracingRequestHandler} which traces using the default {@link Tracer}. */
  protected TracingSQSMessageHandler() {
    super(new AwsLambdaMessageTracer());
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link Tracer}.
   */
  protected TracingSQSMessageHandler(Tracer tracer) {
    super(new AwsLambdaMessageTracer(tracer));
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link
   * AwsLambdaMessageTracer}.
   */
  protected TracingSQSMessageHandler(AwsLambdaMessageTracer tracer) {
    super(tracer);
  }

  protected final void handleEvent(SQSEvent event, Context context) {
    for (SQSMessage message : event.getRecords()) {
      Span span = getTracer().startSpan(message);
      Throwable error = null;
      try (Scope ignored = getTracer().startScope(span)) {
        handleEvent(event, context);
      } catch (Throwable t) {
        error = t;
        throw t;
      } finally {
        if (error != null) {
          getTracer().endExceptionally(span, error);
        } else {
          getTracer().end(span);
        }
      }
    }
  }

  protected abstract void handleMessage(SQSMessage message, SQSEvent event, Context context);
}
