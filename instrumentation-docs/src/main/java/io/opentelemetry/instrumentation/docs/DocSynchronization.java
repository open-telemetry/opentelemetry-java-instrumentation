/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static java.lang.System.exit;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.instrumentation.docs.auditors.DocumentationAuditor;
import io.opentelemetry.instrumentation.docs.auditors.SupportedLibrariesAuditor;
import io.opentelemetry.instrumentation.docs.auditors.SuppressionListAuditor;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private static final String CLEAN_RESULT = "clean";
  private static final String DRIFT_RESULT = "drift";

  public static void main(String[] args) {
    try {
      Path auditResultPath = Path.of(args[0]);
      HttpClient client = HttpClient.newHttpClient();
      boolean hasDrift = false;
      boolean hasErrors = false;
      StringBuilder driftMessage = new StringBuilder();
      StringBuilder errorMessage = new StringBuilder();

      for (DocumentationAuditor auditor : AUDITORS) {
        try {
          logger.info("Running " + auditor.getAuditorName() + "...");
          Optional<String> result = auditor.performAudit(client);

          if (result.isPresent()) {
            hasDrift = true;
            if (!driftMessage.isEmpty()) {
              driftMessage.append("\n\n");
            }
            driftMessage.append(result.get());
          }
        } catch (IOException | InterruptedException | RuntimeException e) {
          logger.severe("Error running " + auditor.getAuditorName() + ": " + e.getMessage());
          hasErrors = true;
          if (!errorMessage.isEmpty()) {
            errorMessage.append("\n\n");
          }
          errorMessage
              .append("Error in ")
              .append(auditor.getAuditorName())
              .append(": ")
              .append(e.getMessage());
        }
      }

      if (hasErrors) {
        if (hasDrift) {
          logger.warning("Partial drift detected (audit did not complete):\n" + driftMessage);
        }
        logger.severe("Audit execution errors:\n" + errorMessage);
        exit(1);
      } else if (hasDrift) {
        // Add custom markers and "How to Fix" section for GitHub workflow extraction
        StringBuilder finalMessage = new StringBuilder();
        finalMessage.append("=== AUDIT_FAILURE_START ===\n");
        finalMessage.append(driftMessage.toString());
        finalMessage.append("\n\n## How to Fix\n\n");
        finalMessage.append(
            "For guidance on updating the OpenTelemetry.io documentation, see: [Documenting Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/contributing/documenting-instrumentation.md#opentelemetryio)");
        finalMessage.append("\n=== AUDIT_FAILURE_END ===");

        logger.severe(finalMessage.toString());
        writeAuditResult(auditResultPath, DRIFT_RESULT);
      } else {
        logger.info("All documentation audits passed successfully.");
        writeAuditResult(auditResultPath, CLEAN_RESULT);
      }

    } catch (IOException | RuntimeException e) {
      logger.severe("Error running documentation audits: " + e.getMessage());
      logger.severe(Arrays.toString(e.getStackTrace()));
      exit(1);
    }
  }

  private static void writeAuditResult(Path auditResultPath, String result) throws IOException {
    Path parent = auditResultPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(auditResultPath, result, UTF_8);
  }

  private DocSynchronization() {}
}
