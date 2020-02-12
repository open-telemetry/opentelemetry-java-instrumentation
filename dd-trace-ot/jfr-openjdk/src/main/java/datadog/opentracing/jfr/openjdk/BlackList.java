package datadog.opentracing.jfr.openjdk;

import datadog.opentracing.DDTraceOTInfo;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BlackList {

  private static final String VERSION = DDTraceOTInfo.JAVA_VERSION.split("\\.")[0];
  private static final Set<String> VERSION_BLACK_LIST;

  static {
    final Set<String> blackList = new HashSet<>();
    // Java 9 and 10 throw seg fault on MacOS if events are used in premain.
    // Since these versions are not LTS we just disable profiling events for them.
    blackList.add("9");
    blackList.add("10");
    VERSION_BLACK_LIST = Collections.unmodifiableSet(blackList);
  }

  public static void checkBlackList() throws ClassNotFoundException {
    if (VERSION_BLACK_LIST.contains(VERSION)) {
      throw new ClassNotFoundException("Blacklisted java version: " + DDTraceOTInfo.JAVA_VERSION);
    }
  }
}
