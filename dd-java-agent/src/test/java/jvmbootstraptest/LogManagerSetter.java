package jvmbootstraptest;

import java.lang.instrument.Instrumentation;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.logging.LogManager;

public class LogManagerSetter {
  private static final DatagramSocket socket;
  private static final int localPort;

  static {
    try {
      socket = new DatagramSocket(0);
      localPort = socket.getLocalPort();
    } catch (SocketException e) {
      throw new RuntimeException(e);
    }
  }

  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    // set jmxfetch port in premain before tracer's premain runs
    System.setProperty("dd.jmxfetch.statsd.port", Integer.toString(localPort));
    System.setProperty("dd.jmxfetch.statsd.host", "localhost");
  }

  public static void main(String... args) throws Exception {
    try {
      // block until jmxfetch sends data
      final byte[] buf = new byte[1500];
      final DatagramPacket packet = new DatagramPacket(buf, buf.length);
      socket.receive(packet);
    } finally {
      socket.close();
    }
    System.setProperty("java.util.logging.manager", CustomLogManager.class.getName());
    customAssert(
        LogManager.getLogManager().getClass(),
        CustomLogManager.class,
        "Javaagent should not prevent setting a custom log manager");
  }

  private static void customAssert(Object got, Object expected, String assertionMessage) {
    if ((null == got && got != expected) || !got.equals(expected)) {
      throw new RuntimeException(
          "Assertion failed. Expected <" + expected + "> got <" + got + "> " + assertionMessage);
    }
  }
}
