/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class ElasticJobSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-elasticjob-3.0";
  private static final Instrumenter<ElasticJobProcessRequest, Void> INSTRUMENTER =
      ElasticJobInstrumenterFactory.create(INSTRUMENTATION_NAME);
  private static final ElasticJobHelper HELPER = ElasticJobHelper.create(INSTRUMENTER);

  public static ElasticJobHelper helper() {
    return HELPER;
  }

  private ElasticJobSingletons() {}
}
