/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static java.lang.System.exit;

import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.yaml.snakeyaml.Yaml;

/**
 * This class is responsible for auditing and synchronizing documentation using the instrumentation
 * list yaml.
 */
public class DocSynchronization {
  private static final Logger logger = Logger.getLogger(DocSynchronization.class.getName());

  private static final String DOCUMENTATION_DISABLE_LIST =
      "https://raw.githubusercontent.com/open-telemetry/opentelemetry.io/refs/heads/main/content/en/docs/zero-code/java/agent/disable.md";

  // Used for consolidating instrumentation groups where we override the key with the value
  private static final Map<String, String> INSTRUMENTATION_DISABLE_OVERRIDES =
      Map.of("akka-actor-fork-join", "akka-actor");

  private static final List<String> INSTRUMENTATION_EXCLUSIONS =
      List.of("resources", "spring-boot-resources");

  private DocSynchronization() {}

  /**
   * Retrieves contents of the disable page from the main branch of the documentation site.
   *
   * @return the file content as a string
   */
  private static String getDocumentationDisableList(HttpClient client)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create(DOCUMENTATION_DISABLE_LIST)).build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return response.body();
    }
    throw new IOException("Failed to fetch instrumentation list: " + response);
  }

  @SuppressWarnings("unchecked")
  public static List<String> parseInstrumentationList(String fileContent) {
    List<String> instrumentationList = new ArrayList<>();
    Yaml yaml = new Yaml();
    Map<String, Object> data = yaml.load(fileContent);

    if (data != null && data.get("libraries") instanceof Map) {
      Map<String, List<Map<String, Object>>> libraries =
          (Map<String, List<Map<String, Object>>>) data.get("libraries");
      for (List<Map<String, Object>> libraryGroup : libraries.values()) {
        for (Map<String, Object> instrumentation : libraryGroup) {
          if (instrumentation.get("name") instanceof String) {
            instrumentationList.add((String) instrumentation.get("name"));
          }
        }
      }
    }
    return instrumentationList;
  }

  /**
   * Identifies missing items in the instrumentation list that are not present in the documentation
   * disable list. Takes into account any overrides specified in INSTRUMENTATION_DISABLE_OVERRIDES
   * and excludes items listed in INSTRUMENTATION_EXCLUSIONS.
   *
   * @param documentationDisabledList a list of items that are documented
   * @param instrumentationList a list of instrumentations from the instrumentation list
   * @return a list of missing items that should be documented
   */
  public static List<String> identifyMissingItems(
      List<String> documentationDisabledList, List<String> instrumentationList) {
    Set<String> documentationDisabledSet = new HashSet<>(documentationDisabledList);

    Set<String> sanitizedInstrumentationItems = new TreeSet<>();
    for (String item : instrumentationList) {
      sanitizedInstrumentationItems.add(item.replaceFirst("-[0-9].*$", ""));
    }

    List<String> missingItems = new ArrayList<>();
    for (String item : sanitizedInstrumentationItems) {
      if (INSTRUMENTATION_EXCLUSIONS.contains(item)) {
        continue; // Skip excluded items
      }
      String itemToCheck = INSTRUMENTATION_DISABLE_OVERRIDES.getOrDefault(item, item);
      boolean found = false;
      for (String disabledItem : documentationDisabledSet) {
        if (itemToCheck.startsWith(disabledItem)) {
          found = true;
          break;
        }
      }
      if (!found) {
        missingItems.add(item);
      }
    }
    return missingItems;
  }

  /**
   * Retrieves the instrumentation list yaml file.
   *
   * @return a string representation of the instrumentation list
   */
  @Nullable
  private static String getInstrumentationList() {
    // Identify path to repo so we can use absolute paths
    String baseRepoPath = System.getProperty("basePath");
    if (baseRepoPath == null) {
      baseRepoPath = "./";
    } else {
      baseRepoPath += "/";
    }

    String file = baseRepoPath + "docs/instrumentation-list.yaml";
    return FileManager.readFileToString(file);
  }

  /**
   * Parses the documentation disabled list from the file content and turns it into a list of
   * instrumentation names.
   *
   * @param fileContent the content of the disable.md documentation file
   * @return a list of instrumentation names that are documented
   */
  public static List<String> parseDocumentationDisabledList(String fileContent) {
    List<String> instrumentationList = new ArrayList<>();
    String[] lines = fileContent.split("\\R");
    for (String line : lines) {
      if (line.trim().startsWith("|")) {
        String[] parts = line.split("\\|");
        if (parts.length > 2) {
          String potentialName = parts[2].trim();
          if (potentialName.startsWith("`") && potentialName.endsWith("`")) {
            String name = potentialName.substring(1, potentialName.length() - 1);
            instrumentationList.add(name);
          }
        }
      }
    }
    return instrumentationList;
  }

  public static void main(String[] args) {
    HttpClient client = HttpClient.newHttpClient();

    try {
      String content = getDocumentationDisableList(client);
      List<String> disabledList = parseDocumentationDisabledList(content);

      String instrumentationListContent = Objects.requireNonNull(getInstrumentationList());
      List<String> instrumentationList = parseInstrumentationList(instrumentationListContent);

      List<String> missingItems = identifyMissingItems(disabledList, instrumentationList);

      if (missingItems.isEmpty()) {
        logger.info("No missing items found.");
      } else {
        StringBuilder sb = new StringBuilder();
        sb.append("Missing Instrumentation List (")
            .append(missingItems.size())
            .append(" item(s) missing):\n");
        missingItems.forEach(item -> sb.append("  - ").append(item).append("\n"));
        logger.severe(sb.toString());
        exit(1);
      }

    } catch (IOException | InterruptedException e) {
      logger.severe("Error fetching instrumentation list: " + e.getMessage());
      logger.severe(Arrays.toString(e.getStackTrace()));
      exit(1);
    }
  }
}
