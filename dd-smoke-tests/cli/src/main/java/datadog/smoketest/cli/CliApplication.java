package datadog.smoketest.cli;

import datadog.trace.api.Trace;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/** Simple application that makes a request to example.com then quits. */
public class CliApplication {

  public static void main(final String[] args) throws InterruptedException {
    final CliApplication app = new CliApplication();

    System.out.println("Making request");

    app.makeRequest();

    System.out.println("Finished making request");
  }

  @Trace(operationName = "example")
  public void makeRequest() {
    try {
      final URL url = new URL("http://www.example.com/");
      final URLConnection connection = url.openConnection();
      connection.setConnectTimeout(1000);
      connection.connect();
      connection.getInputStream();
    } catch (final IOException e) {
      // Ignore.  The goal of this app is to attempt the connection not necessarily to succeed
    }
  }
}
