/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static java.lang.System.exit;

import io.opentelemetry.instrumentation.docs.auditors.DocumentationAuditor;
import io.opentelemetry.instrumentation.docs.auditors.SupportedLibrariesAuditor;
import io.opentelemetry.instrumentation.docs.auditors.SuppressionListAuditor;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * This class is responsible for auditing and synchronizing documentation between the source of
 * truth (this repo) and the opentelemetry.io site.
 */
public class DocSynchronization {
  private static final Logger logger = Logger.getLogger(DocSynchronization.class.getName());

  private static final List<DocumentationAuditor> AUDITORS =
      List.of(new SuppressionListAuditor(), new SupportedLibrariesAuditor());

  public static void main(String[] args) {
    HttpClient client = HttpClient.newHttpClient();

    try {
      boolean hasFailures = false;
      StringBuilder combinedMessage = new StringBuilder();

      for (DocumentationAuditor auditor : AUDITORS) {
        try {
          logger.info("Running " + auditor.getAuditorName() + "...");
          Optional<String> result = auditor.performAudit(client);

          if (result.isPresent()) {
            hasFailures = true;
            if (!combinedMessage.isEmpty()) {
              combinedMessage.append("\n\n");
            }
            combinedMessage.append(result.get());
          }
        } catch (IOException | InterruptedException | RuntimeException e) {
          logger.severe("Error running " + auditor.getAuditorName() + ": " + e.getMessage());
          hasFailures = true;
          if (!combinedMessage.isEmpty()) {
            combinedMessage.append("\n\n");
          }
          combinedMessage
              .append("Error in ")
              .append(auditor.getAuditorName())
              .append(": ")
              .append(e.getMessage());
        }
      }

      if (hasFailures) {
        // Add custom markers and "How to Fix" section for GitHub workflow extraction
        StringBuilder finalMessage = new StringBuilder();
        finalMessage.append("=== AUDIT_FAILURE_START ===\n");
        finalMessage.append(combinedMessage.toString());
        finalMessage.append("\n\n## How to Fix\n\n");
        finalMessage.append(
            "For guidance on updating the OpenTelemetry.io documentation, see: [Documenting Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/contributing/documenting-instrumentation.md#opentelemetryio)");
        finalMessage.append("\n=== AUDIT_FAILURE_END ===");

        logger.severe(finalMessage.toString());
        exit(1);
      } else {
        logger.info("All documentation audits passed successfully.");
      }

    } catch (RuntimeException e) {
      logger.severe("Error running documentation audits: " + e.getMessage());
      logger.severe(Arrays.toString(e.getStackTrace()));
      exit(1);
    }
  }

  private DocSynchronization() {}
}
