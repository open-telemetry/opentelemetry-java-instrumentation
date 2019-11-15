package datadog.smoketest.cli;

import datadog.trace.api.Trace;

/** Simple application that sleeps then quits. */
public class CliApplication {

  public static void main(final String[] args) throws InterruptedException {
    final CliApplication app = new CliApplication();

    // Sleep to ensure all of the processes are running
    Thread.sleep(5000);

    System.out.println("Calling example trace");

    app.exampleTrace();

    System.out.println("Finished calling example trace");
  }

  @Trace(operationName = "example")
  public void exampleTrace() throws InterruptedException {
    Thread.sleep(500);
  }
}
