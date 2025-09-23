/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

/**
 * Represents the data in a metadata.yaml file. This class is internal and is hence not for public
 * use. Its APIs are unstable and can change at any time.
 */
public enum SemanticConvention {
  HTTP_CLIENT_SPANS,
  HTTP_CLIENT_METRICS,
  HTTP_SERVER_SPANS,
  HTTP_SERVER_METRICS,
  RPC_CLIENT_SPANS,
  RPC_CLIENT_METRICS,
  RPC_SERVER_SPANS,
  RPC_SERVER_METRICS,
  MESSAGING_SPANS,
  DATABASE_CLIENT_SPANS,
  DATABASE_CLIENT_METRICS,
  DATABASE_POOL_METRICS,
  JVM_RUNTIME_METRICS,
  GRAPHQL_SERVER_SPANS,
  FAAS_SERVER_SPANS,
  GENAI_CLIENT_SPANS,
  GENAI_CLIENT_METRICS,
}
