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

  public static Map<String, String> parseMap(final String str, final String settingName) {
    if (str == null || str.trim().isEmpty()) {
      return Collections.emptyMap();
    }
    if (!str.matches("(([^,:]+:[^,:]*,)*([^,:]+:[^,:]*),?)?")) {
      log.warn(
          "Invalid config for {}: '{}'. Must match 'key1:value1,key2:value2'.", settingName, str);
      return Collections.emptyMap();
    }

    final String[] tokens = str.split(",", -1);
    final Map<String, String> map = new HashMap<>(tokens.length + 1, 1f);

    for (final String token : tokens) {
      final String[] keyValue = token.split(":", -1);
      if (keyValue.length == 2) {
        final String key = keyValue[0].trim();
        final String value = keyValue[1].trim();
        if (value.length() <= 0) {
          log.warn("Ignoring empty value for key '{}' in config for {}", key, settingName);
          continue;
        }
        map.put(key, value);
      }
    }
    return Collections.unmodifiableMap(map);
  }
}
