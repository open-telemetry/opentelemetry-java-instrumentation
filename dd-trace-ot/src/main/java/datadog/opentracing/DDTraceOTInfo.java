package datadog.opentracing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DDTraceOTInfo {

  public static final String JAVA_VERSION = System.getProperty("java.version", "unknown");
  public static final String JAVA_VM_NAME = System.getProperty("java.vm.name", "unknown");
  public static final String JAVA_VM_VENDOR = System.getProperty("java.vm.vendor", "unknown");

  public static final String VERSION;

  public static final String CONTAINER_ID;

  static {
    String v;
    try {
      final StringBuilder sb = new StringBuilder();

      final BufferedReader br =
          new BufferedReader(
              new InputStreamReader(
                  DDTraceOTInfo.class.getResourceAsStream("/dd-trace-ot.version"), "UTF-8"));
      for (int c = br.read(); c != -1; c = br.read()) sb.append((char) c);

      v = sb.toString().trim();
    } catch (final Exception e) {
      v = "unknown";
    }
    VERSION = v;
    log.info("dd-trace - version: {}", v);

    ContainerInfo containerInfo = null;

    if (ContainerInfo.isRunningInContainer()) {
      try {
        containerInfo = ContainerInfo.fromDefaultProcFile();
      } catch (final IOException | ParseException e) {
        log.error("Unable to parse proc file");
      }
    }

    CONTAINER_ID = containerInfo == null ? null : containerInfo.getContainerId();
  }

  public static void main(final String... args) {
    System.out.println(VERSION);
  }
}
