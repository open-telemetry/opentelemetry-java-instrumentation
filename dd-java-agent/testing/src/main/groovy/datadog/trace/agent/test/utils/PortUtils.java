package datadog.trace.agent.test.utils;

import java.io.IOException;
import java.net.ServerSocket;

public class PortUtils {

  public static int UNUSABLE_PORT = 61;

  /** Open up a random, reusable port. */
  public static int randomOpenPort() {
    final ServerSocket socket;
    try {
      socket = new ServerSocket(0);
      socket.setReuseAddress(true);
      socket.close();
      return socket.getLocalPort();
    } catch (final IOException ioe) {
      ioe.printStackTrace();
      return -1;
    }
  }
}
