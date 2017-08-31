package com.datadoghq.trace.agent;

import com.datadoghq.trace.resolver.FactoryUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

  /* For testing purpose */
  InstrumentationChecker(
      final Map<String, List<ArtifactSupport>> rules, final Map<String, String> frameworks) {
    this.rules = rules;
  }

  public InstrumentationChecker() {
    rules =
        FactoryUtils.loadConfigFromResource(
            CONFIG_FILE, new TypeReference<Map<String, List<ArtifactSupport>>>() {});
  }

  public List<String> getUnsupportedRules(ClassLoader classLoader) {
    log.debug("Checking rule compatibility on classloader {}", classLoader);

    final List<String> unsupportedRules = new ArrayList<>();
    for (final String rule : rules.keySet()) {

      // Check rules
      boolean supported = false;
      for (final ArtifactSupport check : rules.get(rule)) {
        log.debug("Checking rule {}", check);

        boolean matched =
            (check.identifyingPresentClasses != null
                    && !check.identifyingPresentClasses.entrySet().isEmpty())
                || (check.identifyingMissingClasses != null
                    && !check.identifyingMissingClasses.isEmpty());
        if (check.identifyingPresentClasses != null) {
          for (final Map.Entry<String, String> identifier :
              check.identifyingPresentClasses.entrySet()) {
            final boolean classPresent = isClassPresent(identifier.getKey(), classLoader);
            if (!classPresent) {
              log.debug(
                  "Instrumentation {} not applied due to missing class {}.", rule, identifier);
            } else {
              String identifyingMethod = identifier.getValue();
              if (identifyingMethod != null && !identifyingMethod.isEmpty()) {
                Class clazz = getClassIfPresent(identifier.getKey(), classLoader);
                // already confirmed above the class is there.
                Method[] declaredMethods = clazz.getDeclaredMethods();
                boolean methodFound = false;
                for (Method m : declaredMethods) {
                  if (m.getName().equals(identifyingMethod)) {
                    methodFound = true;
                    break;
                  }
                }
                if (!methodFound) {
                  log.debug(
                      "Instrumentation {} not applied due to missing method {}.{}",
                      rule,
                      identifier.getKey(),
                      identifyingMethod);
                  matched = false;
                }
              }
            }
            matched &= classPresent;
          }
        }
        if (check.identifyingMissingClasses != null) {
          for (final String identifyingClass : check.identifyingMissingClasses) {
            final boolean classMissing = !isClassPresent(identifyingClass, classLoader);
            if (!classMissing) {
              log.debug(
                  "Instrumentation {} not applied due to present class {}.",
                  rule,
                  identifyingClass);
            }
            matched &= classMissing;
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

  static boolean isClassPresent(final String identifyingPresentClass, ClassLoader classLoader) {
    return getClassIfPresent(identifyingPresentClass, classLoader) != null;
  }

  static Class getClassIfPresent(final String identifyingPresentClass, ClassLoader classLoader) {
    try {
      return Class.forName(identifyingPresentClass, false, classLoader);
    } catch (final Exception e) {
      return null;
    }
  }

  @Data
  static class ArtifactSupport {
    private String artifact;

    @JsonProperty("supported_version")
    private String supportedVersion;

    @JsonProperty("identifying_present_classes")
    private Map<String, String> identifyingPresentClasses = Collections.emptyMap();

    @JsonProperty("identifying_missing_classes")
    private List<String> identifyingMissingClasses = Collections.emptyList();
  }
}
