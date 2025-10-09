/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticjob.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.elasticjob.v3_0.ElasticJobHelper;
import io.opentelemetry.javaagent.instrumentation.elasticjob.v3_0.ElasticJobInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.elasticjob.v3_0.ElasticJobProcessRequest;

public final class ElasticJobSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.elasticjob-3.0";
  private static final Instrumenter<ElasticJobProcessRequest, Void> INSTRUMENTER =
      ElasticJobInstrumenterFactory.create(INSTRUMENTATION_NAME);
  private static final ElasticJobHelper HELPER = ElasticJobHelper.create(INSTRUMENTER);

  public static ElasticJobHelper helper() {
    return HELPER;
  }

  private ElasticJobSingletons() {}
}
