/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.auditors;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Optional;

/**
 * Base interface for auditing documentation synchronization between the instrumentation repository
 * and the OpenTelemetry.io website.
 */
public interface DocumentationAuditor {

  /**
   * Performs an audit by comparing local instrumentation data with remote documentation.
   *
   * @param client HTTP client for making remote requests
   * @return Optional.empty() if successful, or Optional.of(errorMessage) if there are issues
   * @throws IOException if there's an error fetching remote content
   * @throws InterruptedException if the HTTP request is interrupted
   */
  Optional<String> performAudit(HttpClient client) throws IOException, InterruptedException;

  /**
   * Returns the name of this auditor for logging and reporting purposes.
   *
   * @return auditor name
   */
  String getAuditorName();
}
