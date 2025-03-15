/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleParser {

  private static final Pattern variablePattern =
      Pattern.compile("val\\s+(\\w+)\\s*=\\s*\"([^\"]+)\"");

  private static final Pattern muzzlePassBlockPattern =
      Pattern.compile("pass\\s*\\{(.*?)}", Pattern.DOTALL);

  private static final Pattern libraryPattern =
      Pattern.compile("library\\(\"([^\"]+:[^\"]+):([^\"]+)\"\\)");

  private static final Pattern compileOnlyPattern =
      Pattern.compile(
          "compileOnly\\(\"([^\"]+:[^\"]+)(?::[^\"]+)?\"\\)\\s*\\{\\s*version\\s*\\{.*?strictly\\(\"([^\"]+)\"\\).*?}\\s*",
          Pattern.DOTALL);

  private static final Pattern latestDepTestLibraryPattern =
      Pattern.compile("latestDepTestLibrary\\(\"([^\"]+:[^\"]+):([^\"]+)\"\\)");

  private static final Pattern coreJdkPattern = Pattern.compile("coreJdk\\(\\)");

  /**
   * Parses gradle files for muzzle and dependency information
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @return A set of strings summarizing the group, module, and version ranges
   */
  public static Set<String> parseGradleFile(String gradleFileContents, InstrumentationType type) {
    Set<String> results = new HashSet<>();
    Map<String, String> variables = extractVariables(gradleFileContents);

    if (type.equals(InstrumentationType.JAVAAGENT)) {
      results.addAll(parseMuzzle(gradleFileContents, variables));
    } else {
      results.addAll(parseLibraryDependencies(gradleFileContents, variables));
    }

    return results;
  }

  /**
   * Parses the "muzzle" block from the given Gradle file content and extracts information about
   * each "pass { ... }" entry, returning a set of version summary strings.
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @param variables Map of variable names to their values
   * @return A set of strings summarizing the group, module, and version ranges
   */
  private static Set<String> parseMuzzle(String gradleFileContents, Map<String, String> variables) {
    Set<String> results = new HashSet<>();
    Matcher passBlockMatcher = muzzlePassBlockPattern.matcher(gradleFileContents);

    while (passBlockMatcher.find()) {
      String passBlock = passBlockMatcher.group(1);

      if (coreJdkPattern.matcher(passBlock).find()) {
        results.add("Java 8+");
      }

      String group = extractValue(passBlock, "group\\.set\\(\"([^\"]+)\"\\)");
      String module = extractValue(passBlock, "module\\.set\\(\"([^\"]+)\"\\)");
      String versionRange = extractValue(passBlock, "versions\\.set\\(\"([^\"]+)\"\\)");

      if (group != null && module != null && versionRange != null) {
        String summary = group + ":" + module + ":" + interpolate(versionRange, variables);
        results.add(summary);
      }
    }
    return results;
  }

  /**
   * Parses the "dependencies" block from the given Gradle file content and extracts information
   * about what library versions are supported. Looks for library() and compileOnly() blocks for
   * lower bounds, and latestDepTestLibrary() for upper bounds.
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @param variables Map of variable names to their values
   * @return A set of strings summarizing the group, module, and versions
   */
  private static Set<String> parseLibraryDependencies(
      String gradleFileContents, Map<String, String> variables) {
    Map<String, String> versions = new HashMap<>();

    Matcher libraryMatcher = libraryPattern.matcher(gradleFileContents);

    while (libraryMatcher.find()) {
      String groupAndArtifact = libraryMatcher.group(1);
      String version = libraryMatcher.group(2);
      versions.put(groupAndArtifact, version);
    }

    Matcher compileOnlyMatcher = compileOnlyPattern.matcher(gradleFileContents);
    while (compileOnlyMatcher.find()) {
      String groupAndArtifact = compileOnlyMatcher.group(1);
      String version = compileOnlyMatcher.group(2);
      versions.put(groupAndArtifact, version);
    }

    Matcher latestDepTestLibraryMatcher = latestDepTestLibraryPattern.matcher(gradleFileContents);
    while (latestDepTestLibraryMatcher.find()) {
      String groupAndArtifact = latestDepTestLibraryMatcher.group(1);
      String version = latestDepTestLibraryMatcher.group(2);
      if (versions.containsKey(groupAndArtifact)) {
        versions.put(groupAndArtifact, versions.get(groupAndArtifact) + "," + version);
      }
    }

    Set<String> results = new HashSet<>();
    for (Map.Entry<String, String> entry : versions.entrySet()) {
      if (entry.getValue().contains(",")) {
        results.add(interpolate(entry.getKey() + ":[" + entry.getValue() + ")", variables));
      } else {
        results.add(interpolate(entry.getKey() + ":" + entry.getValue(), variables));
      }
    }

    return results;
  }

  /**
   * Extracts variables from the given Gradle file content.
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @return A map of variable names to their values
   */
  private static Map<String, String> extractVariables(String gradleFileContents) {
    Map<String, String> variables = new HashMap<>();
    Matcher variableMatcher = variablePattern.matcher(gradleFileContents);

    while (variableMatcher.find()) {
      variables.put(variableMatcher.group(1), variableMatcher.group(2));
    }

    return variables;
  }

  /**
   * Interpolates variables in the given text using the provided variable map.
   *
   * @param text Text to interpolate
   * @param variables Map of variable names to their values
   * @return Interpolated text
   */
  private static String interpolate(String text, Map<String, String> variables) {
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      text = text.replace("$" + entry.getKey(), entry.getValue());
    }
    return text;
  }

  /**
   * Utility method to extract the first captured group from matching the given regex.
   *
   * @param text Text to search
   * @param regex Regex with a capturing group
   * @return The first captured group, or null if not found
   */
  private static String extractValue(String text, String regex) {
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(text);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  private GradleParser() {}
}
