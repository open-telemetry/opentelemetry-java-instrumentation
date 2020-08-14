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

package io.opentelemetry.instrumentation.auto.play.v2_3;

import static io.opentelemetry.instrumentation.auto.play.v2_3.PlayTracer.TRACER;

import io.opentelemetry.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.mvc.Result;
import scala.util.Try;

public class RequestCompleteCallback extends scala.runtime.AbstractFunction1<Try<Result>, Object> {

  private static final Logger log = LoggerFactory.getLogger(RequestCompleteCallback.class);

  private final Span span;

  public RequestCompleteCallback(final Span span) {
    this.span = span;
  }

  @Override
  public Object apply(final Try<Result> result) {
    try {
      if (result.isFailure()) {
        TRACER.endExceptionally(span, result.failed().get());
      } else {
        TRACER.end(span);
      }
    } catch (final Throwable t) {
      log.debug("error in play instrumentation", t);
    }
    return null;
  }
}
