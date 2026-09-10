/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static java.util.Collections.emptySet;

import io.opentelemetry.instrumentation.docs.internal.DependencyInfo;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Handles parsing of Gradle build files to extract muzzle and dependency information. */
public class GradleParser {

  private static final Pattern variablePattern =
      Pattern.compile("val\\s+(\\w+)\\s*=\\s*\"([^\"]+)\"");

  private static final Pattern muzzlePassBlockStartPattern = Pattern.compile("\\bpass\\s*\\{");

  private static final Pattern coreJdkPattern = Pattern.compile("coreJdk\\.set\\(true\\)");

  /**
   * Marker comment that excludes a muzzle directive from the generated documentation. Some pass
   * blocks only exist to verify a sub-range or an alternate dependency set, and publishing their
   * version range alongside the module's real range is misleading.
   */
  private static final Pattern docsIgnorePattern =
      Pattern.compile("//\\s*instrumentation-docs:ignore");

  private static final Pattern ifBlockPattern =
      Pattern.compile("if\\s*\\([^)]*\\)\\s*\\{.*?}", Pattern.DOTALL);

  private static final Pattern otelJavaBlockPattern =
      Pattern.compile("otelJava\\s*\\{.*?}", Pattern.DOTALL);

  private static final Pattern minJavaVersionPattern =
      Pattern.compile("minJavaVersionSupported\\.set\\(JavaVersion\\.VERSION_(\\d+)\\)");

  /**
   * Parses gradle files for muzzle and dependency information
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @return A set of strings summarizing the group, module, and version ranges
   */
  public static DependencyInfo parseGradleFile(
      String gradleFileContents, InstrumentationType type) {
    if (type.equals(InstrumentationType.LIBRARY)) {
      return new DependencyInfo(emptySet(), null);
    }

    Map<String, String> variables = extractVariables(gradleFileContents);
    return parseMuzzle(gradleFileContents, variables);
  }

  /**
   * Parses the "muzzle" block from the given Gradle file content and extracts information about
   * each "pass { ... }" entry, returning a set of version summary strings.
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @param variables Map of variable names to their values
   * @return A set of strings summarizing the group, module, and version ranges
   */
  private static DependencyInfo parseMuzzle(
      String gradleFileContents, Map<String, String> variables) {
    Set<String> results = new HashSet<>();

    Integer minJavaVersion = parseMinJavaVersion(gradleFileContents);

    for (String passBlock : extractPassBlocks(gradleFileContents)) {
      if (docsIgnorePattern.matcher(passBlock).find()) {
        continue;
      }

      if (coreJdkPattern.matcher(passBlock).find()) {
        if (minJavaVersion != null) {
          results.add("Java " + minJavaVersion + "+");
        } else {
          results.add("Java 8+");
        }
      }

      String group = extractValue(passBlock, "group\\.set\\(\"([^\"]+)\"\\)");
      String module = extractValue(passBlock, "module\\.set\\(\"([^\"]+)\"\\)");
      String versionRange = extractValue(passBlock, "versions\\.set\\(\"([^\"]+)\"\\)");

      if (group != null && module != null && versionRange != null) {
        String summary = group + ":" + module + ":" + interpolate(versionRange, variables);
        results.add(summary);
      }
    }
    return new DependencyInfo(results, minJavaVersion);
  }

  /**
   * Extracts the body of each muzzle "pass { ... }" block. Braces are matched by depth rather than
   * with a regex so that a block containing a brace, such as a comment referencing {@code
   * io.opentelemetry.context.{Context,Scope}}, is captured in full instead of being truncated at
   * that brace.
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @return The body of each pass block, in the order they appear
   */
  private static List<String> extractPassBlocks(String gradleFileContents) {
    List<String> passBlocks = new ArrayList<>();
    Matcher blockStartMatcher = muzzlePassBlockStartPattern.matcher(gradleFileContents);
    int searchFrom = 0;

    while (blockStartMatcher.find(searchFrom)) {
      int bodyStart = blockStartMatcher.end();
      int depth = 1;
      int position = bodyStart;

      while (position < gradleFileContents.length() && depth > 0) {
        char c = gradleFileContents.charAt(position);
        if (c == '{') {
          depth++;
        } else if (c == '}') {
          depth--;
        }
        position++;
      }

      if (depth != 0) {
        // unbalanced braces, the file is not something we can reason about
        break;
      }

      passBlocks.add(gradleFileContents.substring(bodyStart, position - 1));
      searchFrom = position;
    }

    return passBlocks;
  }

  @Nullable
  public static Integer parseMinJavaVersion(String gradleFileContents) {
    List<int[]> excludedRanges = new ArrayList<>();

    // Identify all if-block ranges so we can exclude them
    Matcher ifBlockMatcher = ifBlockPattern.matcher(gradleFileContents);
    while (ifBlockMatcher.find()) {
      excludedRanges.add(new int[] {ifBlockMatcher.start(), ifBlockMatcher.end()});
    }

    Matcher otelJavaMatcher = otelJavaBlockPattern.matcher(gradleFileContents);
    while (otelJavaMatcher.find()) {
      int blockStart = otelJavaMatcher.start();

      if (isInExcludedRange(blockStart, excludedRanges)) {
        continue; // Skip blocks inside 'if' statements
      }

      String otelJavaBlock = otelJavaMatcher.group();
      Matcher versionMatcher = minJavaVersionPattern.matcher(otelJavaBlock);
      if (versionMatcher.find()) {
        return Integer.parseInt(versionMatcher.group(1));
      }
    }

    return null;
  }

  private static boolean isInExcludedRange(int position, List<int[]> ranges) {
    for (int[] range : ranges) {
      if (position >= range[0] && position <= range[1]) {
        return true;
      }
    }
    return false;
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
  @Nullable
  private static String extractValue(String text, String regex) {
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(text);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  public static Set<String> extractVersions(
      Path moduleRoot, List<Path> gradleFiles, InstrumentationModule module) {
    Set<String> allVersions = new HashSet<>();
    gradleFiles.forEach(file -> processGradleFile(moduleRoot, file, allVersions, module));
    return allVersions;
  }

  private static void processGradleFile(
      Path moduleRoot, Path filePath, Set<String> versions, InstrumentationModule module) {
    String fileContents = FileManager.readFileToString(filePath);
    if (fileContents == null) {
      return;
    }

    Optional<InstrumentationType> type = determineInstrumentationType(moduleRoot, filePath);
    if (type.isEmpty()) {
      return;
    }

    if (type.get() == InstrumentationType.LIBRARY) {
      module.setHasStandaloneLibrary(true);
      return;
    }

    if (type.get() == InstrumentationType.JAVAAGENT) {
      module.setHasJavaAgent(true);
    }

    DependencyInfo dependencyInfo = parseGradleFile(fileContents, type.get());
    versions.addAll(dependencyInfo.versions());
    if (dependencyInfo.minJavaVersionSupported() != null) {
      module.setMinJavaVersion(dependencyInfo.minJavaVersionSupported());
    }
  }

  /**
   * Determines the instrumentation type from the first segment of the build file's path relative to
   * the module root. Resolving against the module root rather than scanning the absolute path keeps
   * an ancestor checkout directory named {@code javaagent} or {@code library} from being mistaken
   * for the module's own type.
   */
  private static Optional<InstrumentationType> determineInstrumentationType(
      Path moduleRoot, Path filePath) {
    Path relativePath = moduleRoot.relativize(filePath);
    if (relativePath.getNameCount() < 2) {
      return Optional.empty();
    }

    String type = relativePath.getName(0).toString();
    if (type.equals("javaagent")) {
      return Optional.of(InstrumentationType.JAVAAGENT);
    } else if (type.equals("library")) {
      return Optional.of(InstrumentationType.LIBRARY);
    }
    return Optional.empty();
  }

  private GradleParser() {}
}
