package datadog.trace.common.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Config {

  public static String getPropOrEnv(final String name) {
    return System.getProperty(name, System.getenv(propToEnvName(name)));
  }

  public static String propToEnvName(final String name) {
    return name.toUpperCase().replace(".", "_");
  }

  public static Map<String, String> parseMap(final String str) {
    if (str == null || str.trim().isEmpty()) {
      return Collections.emptyMap();
    }
    if (!str.matches("(([^,:]+:[^,:]+,)*([^,:]+:[^,:]+),?)?")) {
      log.warn("Invalid config '{}'. Must match 'key1:value1,key2:value2'.", str);
      return Collections.emptyMap();
    }

    final String[] tokens = str.split(",", -1);
    final Map<String, String> map = new HashMap<>(tokens.length + 1, 1f);

    for (final String token : tokens) {
      final String[] keyValue = token.split(":", -1);
      if (keyValue.length == 2) {
        map.put(keyValue[0].trim(), keyValue[1].trim());
      }
    }
    return Collections.unmodifiableMap(map);
  }
}
