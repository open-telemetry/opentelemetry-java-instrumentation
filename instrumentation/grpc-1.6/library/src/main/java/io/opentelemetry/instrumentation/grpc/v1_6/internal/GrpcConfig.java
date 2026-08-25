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
import io.opentelemetry.instrumentation.api.internal.DeprecatedCaptureNames;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class GrpcConfig {

  private static final Logger logger = Logger.getLogger(GrpcConfig.class.getName());
  private static final Set<String> warnedDeprecatedProperties = ConcurrentHashMap.newKeySet();

  private static final String DEPRECATED_CLIENT_REQUEST_METADATA =
      "otel.instrumentation.grpc.capture-metadata.client.request";
  private static final String DEPRECATED_SERVER_REQUEST_METADATA =
      "otel.instrumentation.grpc.capture-metadata.server.request";
  private static final String CLIENT_REQUEST_METADATA_INCLUDED =
      "otel.instrumentation.grpc.client.request-metadata.included";
  private static final String SERVER_REQUEST_METADATA_INCLUDED =
      "otel.instrumentation.grpc.server.request-metadata.included";

  @Nullable private final IncludeExclude clientRequestMetadata;
  @Nullable private final IncludeExclude serverRequestMetadata;

  public static GrpcConfig create(OpenTelemetry openTelemetry) {
    return new GrpcConfig(
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "grpc"),
        SemconvStability.v3Preview(openTelemetry));
  }

  GrpcConfig(DeclarativeConfigProperties config, boolean v3Preview) {
    clientRequestMetadata =
        getRequestMetadata(
            config,
            "client",
            DEPRECATED_CLIENT_REQUEST_METADATA,
            CLIENT_REQUEST_METADATA_INCLUDED,
            v3Preview);
    serverRequestMetadata =
        getRequestMetadata(
            config,
            "server",
            DEPRECATED_SERVER_REQUEST_METADATA,
            SERVER_REQUEST_METADATA_INCLUDED,
            v3Preview);
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
      DeclarativeConfigProperties config,
      String side,
      String deprecatedProperty,
      String replacementProperty,
      boolean v3Preview) {
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

    if (v3Preview) {
      return null;
    }

    List<String> deprecatedIncluded =
        config.get("capture_metadata").get(side).getScalarList("request", String.class);
    if (deprecatedIncluded == null) {
      return null;
    }

    if (warnedDeprecatedProperties.add(deprecatedProperty)) {
      logger.warning(
          "The "
              + deprecatedProperty
              + " setting and the equivalent declarative configuration property"
              + " are deprecated and will be removed in 3.0. Use "
              + replacementProperty
              + " or equivalent declarative configuration instead.");
    }
    return DeprecatedCaptureNames.toSelector(
        deprecatedIncluded,
        "the " + deprecatedProperty + " setting or equivalent declarative configuration",
        replacementProperty + " or equivalent declarative configuration");
  }
}
