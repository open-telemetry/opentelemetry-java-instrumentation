package com.datadoghq.trace.agent;

import com.datadoghq.trace.resolver.FactoryUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class to check the validity of the classpath concerning the java automated
 * instrumentations
 */
@Slf4j
public class InstrumentationChecker {

  private static final String CONFIG_FILE = "dd-trace-supported-framework";
  private final Map<String, List<ArtifactSupport>> rules;
  private final Map<String, String> frameworks;

  private static InstrumentationChecker INSTANCE;
  private final ClassLoader classLoader;

  /* For testing purpose */
  InstrumentationChecker(
      final Map<String, List<ArtifactSupport>> rules, final Map<String, String> frameworks) {
    this.rules = rules;
    this.frameworks = frameworks;
    this.classLoader = ClassLoader.getSystemClassLoader();
    INSTANCE = this;
  }

  private InstrumentationChecker(final ClassLoader classLoader) {
    this.classLoader = classLoader;
    rules =
        FactoryUtils.loadConfigFromResource(
            CONFIG_FILE, new TypeReference<Map<String, List<ArtifactSupport>>>() {});
    frameworks = scanLoadedLibraries();
  }

  /**
   * Return a list of unsupported rules regarding loading deps
   *
   * @return the list of unsupported rules
   * @param classLoader
   */
  public static synchronized List<String> getUnsupportedRules(final ClassLoader classLoader) {

    if (INSTANCE == null) {
      INSTANCE = new InstrumentationChecker(classLoader);
    }

    return INSTANCE.doGetUnsupportedRules();
  }

  private List<String> doGetUnsupportedRules() {

    final List<String> unsupportedRules = new ArrayList<>();
    for (final String rule : rules.keySet()) {

      // Check rules
      boolean supported = false;
      for (final ArtifactSupport check : rules.get(rule)) {
        if (frameworks.containsKey(check.artifact)) {
          // If no classes to scan, fall back on version regex.
          boolean matched =
              check.identifyingPresentClasses.isEmpty() && check.identifyingMissingClasses.isEmpty()
                  ? Pattern.matches(check.supportedVersion, frameworks.get(check.artifact))
                  : true;
          for (final String identifyingClass : check.identifyingPresentClasses) {
            matched &= isClassPresent(identifyingClass);
          }
          for (final String identifyingClass : check.identifyingMissingClasses) {
            matched &= !isClassPresent(identifyingClass);
          }
          if (!matched) {
            log.debug(
                "Library conflict: supported_version={}, actual_version={}",
                check.supportedVersion,
                frameworks.get(check.artifact));
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

  private boolean isClassPresent(final String identifyingPresentClass) {
    try {
      return identifyingPresentClass != null
          && Class.forName(identifyingPresentClass, false, classLoader) != null;
    } catch (final ClassNotFoundException e) {
      return false;
    }
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

  @Data
  @JsonIgnoreProperties("check")
  static class ArtifactSupport {
    private String artifact;

    @JsonProperty("supported_version")
    private String supportedVersion;

    @JsonProperty("identifying_present_classes")
    private List<String> identifyingPresentClasses = Collections.emptyList();

    @JsonProperty("identifying_missing_classes")
    private List<String> identifyingMissingClasses = Collections.emptyList();
  }
}
