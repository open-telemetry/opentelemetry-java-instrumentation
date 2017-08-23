package com.datadoghq.trace.agent;

import com.datadoghq.trace.resolver.FactoryUtils;
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
  private static InstrumentationChecker INSTANCE;

  private final Map<String, List<ArtifactSupport>> rules;
  private final Map<String, String> frameworks;

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
    log.debug("{} libraries found in the class-path", frameworks.size());

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
          log.trace("{} found in the classpath", file.getName());
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

  private List<String> doGetUnsupportedRules() {

    final List<String> unsupportedRules = new ArrayList<>();
    for (final String rule : rules.keySet()) {

      // Check rules
      boolean supported = false;
      for (final ArtifactSupport check : rules.get(rule)) {
        log.debug("Checking rule {}", check);

        boolean matched = true;
        for (final String identifyingClass : check.identifyingPresentClasses) {
          final boolean classPresent = isClassPresent(identifyingClass);
          if (!classPresent) {
            log.debug(
                "Instrumentation {} not applied due to missing class {}.", rule, identifyingClass);
          }
          matched &= classPresent;
        }
        for (final String identifyingClass : check.identifyingMissingClasses) {
          final boolean classMissing = !isClassPresent(identifyingClass);
          if (!classMissing) {
            log.debug(
                "Instrumentation {} not applied due to present class {}.", rule, identifyingClass);
          }
          matched &= classMissing;
        }

        final boolean useVersionMatching =
            frameworks.containsKey(check.artifact)
                && check.identifyingMissingClasses.isEmpty()
                && check.identifyingPresentClasses.isEmpty();
        if (useVersionMatching) {
          // If no classes to scan, fall back on version regex.
          matched = Pattern.matches(check.supportedVersion, frameworks.get(check.artifact));
          if (!matched) {
            log.debug(
                "Library conflict: supported_version={}, actual_version={}",
                check.supportedVersion,
                frameworks.get(check.artifact));
          }
        }

        supported |= matched;
        if (supported) {
          break;
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
    return isClassPresent(identifyingPresentClass, classLoader);
  }

  static boolean isClassPresent(final String identifyingPresentClass, ClassLoader classLoader) {
    try {
      return identifyingPresentClass != null
          && Class.forName(identifyingPresentClass, false, classLoader) != null;
    } catch (final ClassNotFoundException e) {
      return false;
    }
  }

  @Data
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
