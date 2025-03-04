/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class GradleParser {

  private GradleParser() {}

  private static final Pattern passBlockPattern =
      Pattern.compile("pass\\s*\\{(.*?)\\}", Pattern.DOTALL);

  /**
   * Parses the "muzzle" block from the given Gradle file content and extracts information about
   * each "pass { ... }" entry, returning a list of version summary strings.
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @return A list of strings summarizing the group, module, and version ranges
   */
  public static List<String> parseMuzzleBlock(String gradleFileContents) {
    List<String> results = new ArrayList<>();

    // Regex to find each "pass { ... }" block within the muzzle block
    // Using a reluctant quantifier to match the smallest block
    // that starts with "pass {" and ends with "}" at the same nesting level.
    // This simplified approach assumes no nested braces in the pass block.
    Matcher passBlockMatcher = passBlockPattern.matcher(gradleFileContents);

    while (passBlockMatcher.find()) {
      String passBlock = passBlockMatcher.group(1);

      String group = extractValue(passBlock, "group\\.set\\(\"([^\"]+)\"\\)");
      String module = extractValue(passBlock, "module\\.set\\(\"([^\"]+)\"\\)");
      String versionRange = extractValue(passBlock, "versions\\.set\\(\"([^\"]+)\"\\)");

      if (group != null && module != null && versionRange != null) {
        String summary = group + ":" + module + ":" + versionRange;
        results.add(summary);
      }
    }

    return results;
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
}
