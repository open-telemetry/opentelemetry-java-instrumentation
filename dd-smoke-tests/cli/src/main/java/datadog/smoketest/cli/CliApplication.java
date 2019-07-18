package datadog.smoketest.cli;

/** Simple application that sleeps for a bit than quits. */
public class CliApplication {

  /** @param args Only argument is the sleep delay (in seconds) */
  public static void main(final String[] args) throws InterruptedException {
    final int delay = Integer.parseInt(args[0]);

    System.out.println("Going to shut down after " + delay + "seconds");

    Thread.sleep(delay * 1000);

    System.out.println("Shutting down");
  }
}
