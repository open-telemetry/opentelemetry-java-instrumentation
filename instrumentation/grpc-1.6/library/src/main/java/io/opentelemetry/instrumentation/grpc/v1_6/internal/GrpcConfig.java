/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class GrpcConfig {

  @Nullable private final IncludeExclude clientRequestMetadata;
  @Nullable private final IncludeExclude serverRequestMetadata;

  public static GrpcConfig create(OpenTelemetry openTelemetry) {
    return new GrpcConfig(DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "grpc"));
  }

  // visible for testing
  GrpcConfig(DeclarativeConfigProperties config) {
    clientRequestMetadata = getRequestMetadata(config, "client");
    serverRequestMetadata = getRequestMetadata(config, "server");
  }

  @Nullable
  public IncludeExclude getClientRequestMetadata() {
    return clientRequestMetadata;
  }

  @Nullable
  public IncludeExclude getServerRequestMetadata() {
    return serverRequestMetadata;
  }

  @Nullable
  private static IncludeExclude getRequestMetadata(
      DeclarativeConfigProperties config, String side) {
    DeclarativeConfigProperties requestMetadata = config.get(side).get("request_metadata");
    List<String> included = requestMetadata.getScalarList("included", String.class);
    List<String> excluded = requestMetadata.getScalarList("excluded", String.class);
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(included == null ? emptyList() : included)
            .setExcluded(excluded == null ? emptyList() : excluded)
            .build();
    // an empty selector is equivalent to no selector at all, matching flat configuration where
    // empty property values cannot be distinguished from unset ones
    if (!selector.isEmpty()) {
      return selector;
    }
    return null;
  }
}
