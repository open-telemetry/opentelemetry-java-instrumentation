package com.datadoghq.trace.agent;

import com.datadoghq.trace.resolver.FactoryUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class to check the validity of the classpath concerning the java automated
 * instrumentations
 */
@Slf4j
public class InstrumentationChecker {

  private static final String CONFIG_FILE = "dd-trace-supported-framework.yaml";
  private final Map<String, List<Map<String, String>>> rules;
  private final Map<String, String> frameworks;

  private static InstrumentationChecker INSTANCE;

  /* For testing purpose */
  InstrumentationChecker(
      final Map<String, List<Map<String, String>>> rules, final Map<String, String> frameworks) {
    this.rules = rules;
    this.frameworks = frameworks;
    INSTANCE = this;
  }

  private InstrumentationChecker() {
    rules = FactoryUtils.loadConfigFromResource(CONFIG_FILE, Map.class);
    frameworks = scanLoadedLibraries();
  }

  /**
   * Return a list of unsupported rules regarding loading deps
   *
   * @return the list of unsupported rules
   */
  public static synchronized List<String> getUnsupportedRules() {

    if (INSTANCE == null) {
      INSTANCE = new InstrumentationChecker();
    }

    return INSTANCE.doGetUnsupportedRules();
  }

  private List<String> doGetUnsupportedRules() {

    final List<String> unsupportedRules = new ArrayList<>();
    for (final String rule : rules.keySet()) {

      // Check rules
      boolean supported = false;
      for (final Map<String, String> check : rules.get(rule)) {
        if (frameworks.containsKey(check.get("artifact"))) {
          final boolean matched =
              Pattern.matches(
                  check.get("supported_version"), frameworks.get(check.get("artifact")));
          if (!matched) {
            log.debug(
                "Library conflict: supported_version={}, actual_version={}",
                check.get("supported_version"),
                frameworks.get(check.get("artifact")));
            supported = false;
            break;
          }
          supported = true;
          log.trace("Instrumentation rule={} is supported", rule);
        }
      }

      if (!supported) {
        log.info("Instrumentation rule={} is not supported", rule);
        unsupportedRules.add(rule);
      }
    }

    return unsupportedRules;
  }

  private static Map<String, String> scanLoadedLibraries() {

    final Map<String, String> frameworks = new HashMap<>();

    // Scan classpath provided jars
    final List<File> jars = getJarFiles(System.getProperty("java.class.path"));
    for (final File file : jars) {

      final String jarName = file.getName();
      final String version = extractJarVersion(jarName);

      if (version != null) {

        // Extract artifactId
        final String artifactId = file.getName().substring(0, jarName.indexOf(version) - 1);

        // Store it
        frameworks.put(artifactId, version);
      }
    }

    return frameworks;
  }

  private static List<File> getJarFiles(final String paths) {
    final List<File> filesList = new ArrayList<>();
    for (final String path : paths.split(File.pathSeparator)) {
      final File file = new File(path);
      if (file.isDirectory()) {
        recurse(filesList, file);
      } else {
        if (file.getName().endsWith(".jar")) {
          filesList.add(file);
        }
      }
    }
    return filesList;
  }

  private static void recurse(final List<File> filesList, final File f) {
    final File[] list = f.listFiles();
    for (final File file : list) {
      getJarFiles(file.getPath());
    }
  }

  private static String extractJarVersion(final String jarName) {

    final Pattern versionPattern = Pattern.compile("-(\\d+\\..+)\\.jar");
    final Matcher matcher = versionPattern.matcher(jarName);
    if (matcher.find()) {
      return matcher.group(1);
    } else {
      return null;
    }
  }
}
