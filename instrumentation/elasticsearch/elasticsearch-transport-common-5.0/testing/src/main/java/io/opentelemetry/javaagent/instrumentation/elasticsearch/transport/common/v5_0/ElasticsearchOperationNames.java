/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

public final class ElasticsearchOperationNames {

  /**
   * Returns the operation name that the transport instrumentation reports under the semantic
   * conventions the test runs with: the action class simple name under the old database
   * conventions, the Elasticsearch wire action name under the stable ones.
   */
  public static String operationName(String simpleName, String wireName) {
    // not testing database/dup
    return emitStableDatabaseSemconv() ? wireName : simpleName;
  }

  private ElasticsearchOperationNames() {}
}
