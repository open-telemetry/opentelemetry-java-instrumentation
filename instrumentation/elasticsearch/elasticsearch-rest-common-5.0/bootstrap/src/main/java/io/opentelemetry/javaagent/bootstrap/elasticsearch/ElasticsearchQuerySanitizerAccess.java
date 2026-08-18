/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.elasticsearch;

import io.opentelemetry.instrumentation.api.internal.Initializer;
import javax.annotation.Nullable;

/**
 * A helper to facilitate sanitizing Elasticsearch search query bodies from instrumentation. Because
 * instrumentation runs in the app class loader, it cannot see the JSON parser that ships inside the
 * agent, and the Elasticsearch low-level REST client does not put one on the application classpath.
 * So we use this class in the bootstrap class loader to bridge between the two - the agent class
 * loader registers an implementation that instrumentation can call.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ElasticsearchQuerySanitizerAccess {

  /** Rewrites a search query body so that literal values are replaced with a mask. */
  public interface Sanitizer {
    /** Returns the sanitized body, or {@code null} if the body could not be sanitized. */
    @Nullable
    String sanitize(String body);
  }

  @Nullable private static volatile Sanitizer sanitizer;

  /**
   * Returns the sanitized body, or {@code null} if the body could not be sanitized or no sanitizer
   * has been registered. Callers drop the body when this returns {@code null}, so a missing
   * sanitizer loses the query rather than capturing it unsanitized.
   */
  @Nullable
  public static String sanitize(String body) {
    Sanitizer sanitizer = ElasticsearchQuerySanitizerAccess.sanitizer;
    return sanitizer == null ? null : sanitizer.sanitize(body);
  }

  /**
   * Sets the {@link Sanitizer} to use. This is called from the agent class loader, which is the
   * only place the JSON parser is visible. Instrumentation must not call this.
   */
  @Initializer
  public static void internalSetSanitizer(Sanitizer sanitizer) {
    if (ElasticsearchQuerySanitizerAccess.sanitizer != null) {
      // Only possible by misuse of this API, just ignore.
      return;
    }
    ElasticsearchQuerySanitizerAccess.sanitizer = sanitizer;
  }

  private ElasticsearchQuerySanitizerAccess() {}
}
