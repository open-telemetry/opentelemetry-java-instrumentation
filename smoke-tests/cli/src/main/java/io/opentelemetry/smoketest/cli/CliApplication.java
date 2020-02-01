package io.opentelemetry.smoketest.cli;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

/** Simple application that sleeps then quits. */
public class CliApplication {

  private static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

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
