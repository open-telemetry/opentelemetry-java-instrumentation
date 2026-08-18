/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import javax.annotation.Nullable;

/**
 * Rewrites an Elasticsearch search request body so that every literal value is replaced with the
 * mask {@code "?"} while the structure and the field names are preserved. For example {@code
 * {"query":{"match":{"title":"secret user data"}}}} becomes {@code
 * {"query":{"match":{"title":"?"}}}}.
 *
 * <p>Sanitizing needs a JSON parser, and the Elasticsearch low-level REST client does not put one
 * on the application classpath. So this module only declares the operation; the javaagent supplies
 * an implementation that runs where a parser is available.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@FunctionalInterface
public interface ElasticsearchQuerySanitizer {

  /** Returns the sanitized body, or {@code null} if the body could not be sanitized. */
  @Nullable
  String sanitize(String body);
}
