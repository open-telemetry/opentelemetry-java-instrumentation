/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.auditors;

import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Audits the supported libraries list on the OpenTelemetry.io documentation site to ensure it
 * matches the local supported-libraries.md file.
 */
public class SupportedLibrariesAuditor implements DocumentationAuditor {

  private static final String REMOTE_SUPPORTED_LIBRARIES_URL =
      "https://raw.githubusercontent.com/open-telemetry/opentelemetry.io/refs/heads/main/content/en/docs/zero-code/java/agent/supported-libraries.md";

  @Override
  public Optional<String> performAudit(HttpClient client) throws IOException, InterruptedException {
    List<String> localLibraries = parseLocalSupportedLibraries();
    List<String> remoteLibraries = getRemoteSupportedLibraries(client);
    List<String> missingItems = identifyMissingItems(remoteLibraries, localLibraries);

    if (missingItems.isEmpty()) {
      return Optional.empty();
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Missing Supported Libraries (")
        .append(missingItems.size())
        .append(" item(s) missing from remote):\n");
    missingItems.forEach(item -> sb.append("  - ").append(item).append("\n"));

    return Optional.of(sb.toString());
  }

  @Override
  public String getAuditorName() {
    return "Supported Libraries Auditor";
  }

  /**
   * Retrieves and parses the supported libraries from the remote OpenTelemetry.io site.
   *
   * @param client HTTP client for making requests
   * @return list of library names from the remote site
   */
  private static List<String> getRemoteSupportedLibraries(HttpClient client)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create(REMOTE_SUPPORTED_LIBRARIES_URL)).build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return parseLibraryMarkdownTable(response.body());
    }
    throw new IOException("Failed to fetch remote supported libraries: " + response);
  }

  /**
   * Parses the local supported-libraries.md file to extract library names.
   *
   * @return list of library names from the local file
   */
  private static List<String> parseLocalSupportedLibraries() {
    String baseRepoPath = System.getProperty("basePath");
    if (baseRepoPath == null) {
      baseRepoPath = "./";
    } else {
      baseRepoPath += "/";
    }

    String file = baseRepoPath + "docs/supported-libraries.md";
    String fileContent = FileManager.readFileToString(file);

    if (fileContent == null) {
      return new ArrayList<>();
    }

    return parseLibraryMarkdownTable(fileContent);
  }

  /**
   * Parses markdown content to extract library names from the table.
   *
   * @param content the markdown content
   * @return list of library names
   */
  private static List<String> parseLibraryMarkdownTable(String content) {
    List<String> libraries = new ArrayList<>();
    String[] lines = content.split("\\R");

    boolean inLibrariesSection = false;
    for (String line : lines) {
      // Look for the start of the Libraries/Frameworks section (handle both formats)
      if (line.contains("## Libraries / Frameworks")
          || line.contains("## Libraries and Frameworks")) {
        inLibrariesSection = true;
        continue;
      }

      // Stop when we reach the next major section
      if (inLibrariesSection
          && line.startsWith("##")
          && !line.contains("Libraries / Frameworks")
          && !line.contains("Libraries and Frameworks")) {
        break;
      }

      // Parse table rows in the libraries section
      if (inLibrariesSection
          && line.trim().startsWith("|")
          && !line.contains("Library/Framework")
          && !line.contains("----") // Skip separator rows
          && !line.trim().equals("|")) { // Skip empty rows
        String libraryName = extractLibraryNameFromMarkdownRow(line);
        if (!libraryName.isEmpty() && !libraryName.startsWith("-")) { // Skip separator content
          libraries.add(libraryName);
        }
      }
    }

    return libraries;
  }

  /**
   * Extracts the library name from a markdown table row.
   *
   * @param line the table row line
   * @return the library name, or empty string if not found
   */
  private static String extractLibraryNameFromMarkdownRow(String line) {
    String[] parts = line.split("\\|");
    if (parts.length > 1) {
      String firstColumn = parts[1].trim();

      // Handle markdown links [Text](URL)
      if (firstColumn.startsWith("[") && firstColumn.contains("](")) {
        int endBracket = firstColumn.indexOf("](");
        return firstColumn.substring(1, endBracket);
      }

      return firstColumn;
    }
    return "";
  }

  /**
   * Identifies libraries that are missing from the remote list but present in the local list.
   *
   * @param remoteLibraries libraries from the remote documentation site
   * @param localLibraries libraries from the local supported-libraries.md file
   * @return list of missing libraries
   */
  private static List<String> identifyMissingItems(
      List<String> remoteLibraries, List<String> localLibraries) {
    Set<String> remoteSet = new HashSet<>();
    for (String library : remoteLibraries) {
      remoteSet.add(normalizeLibraryName(library));
    }

    Set<String> missingItems = new TreeSet<>();
    for (String localLibrary : localLibraries) {
      String normalized = normalizeLibraryName(localLibrary);
      if (!remoteSet.contains(normalized)) {
        missingItems.add(localLibrary);
      }
    }

    return new ArrayList<>(missingItems);
  }

  /**
   * Normalizes library names for comparison by removing common variations.
   *
   * @param libraryName the original library name
   * @return normalized library name
   */
  private static String normalizeLibraryName(String libraryName) {
    return libraryName.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
  }
}
