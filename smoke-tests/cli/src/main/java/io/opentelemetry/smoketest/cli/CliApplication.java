/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.smoketest.cli;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

/** Simple application that sleeps then quits. */
public class CliApplication {

  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto");

  public static void main(final String[] args) throws InterruptedException {
    final CliApplication app = new CliApplication();

    // Sleep to ensure all of the processes are running
    Thread.sleep(5000);

    System.out.println("Calling example trace");

    app.exampleTrace();

    System.out.println("Finished calling example trace");
  }

  public void exampleTrace() throws InterruptedException {
    final Span span = TRACER.spanBuilder("example").startSpan();
    try (final Scope scope = TRACER.withSpan(span)) {
      Thread.sleep(500);
      span.end();
    }
  }
}
