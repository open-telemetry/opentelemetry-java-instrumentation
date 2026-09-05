/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import javax.annotation.Nullable;

public class CouchbaseServerTarget {

  private static final int COUCHBASE_DEFAULT_PORT = 11210;
  private static final int COUCHBASES_DEFAULT_PORT = 11207;

  private final DbServerTarget target;
  @Nullable private final String scheme;

  @Nullable
  public static CouchbaseServerTarget direct(@Nullable DbServerTarget target) {
    return target == null ? null : new CouchbaseServerTarget(target, null);
  }

  @Nullable
  public static CouchbaseServerTarget forServiceDiscovery(
      @Nullable String scheme, @Nullable String host) {
    String canonicalScheme;
    if ("couchbase".equalsIgnoreCase(scheme)) {
      canonicalScheme = "couchbase";
    } else if ("couchbases".equalsIgnoreCase(scheme)) {
      canonicalScheme = "couchbases";
    } else {
      return null;
    }
    DbServerTarget target =
        DbServerTarget.builder(defaultPort(scheme)).addEndpoint(cleanHost(host), -1).build();
    return target == null ? null : new CouchbaseServerTarget(target, canonicalScheme);
  }

  private CouchbaseServerTarget(DbServerTarget target, @Nullable String scheme) {
    this.target = target;
    this.scheme = scheme;
  }

  static int defaultPort(@Nullable String scheme) {
    if ("couchbase".equalsIgnoreCase(scheme)) {
      return COUCHBASE_DEFAULT_PORT;
    }
    if ("couchbases".equalsIgnoreCase(scheme)) {
      return COUCHBASES_DEFAULT_PORT;
    }
    return -1;
  }

  public String getAddress() {
    return scheme == null ? target.getAddress() : scheme + "://" + target.getAddress();
  }

  @Nullable
  public Integer getPort() {
    return target.getPort();
  }

  @Nullable
  static String cleanHost(@Nullable String host) {
    if (host == null) {
      return null;
    }
    String cleaned = truncateAt(truncateAt(truncateAt(host.trim(), '/'), '?'), '#');
    int credentialsEnd = cleaned.lastIndexOf('@');
    return credentialsEnd < 0 ? cleaned : cleaned.substring(credentialsEnd + 1);
  }

  private static String truncateAt(String host, char separator) {
    int index = host.indexOf(separator);
    return index < 0 ? host : host.substring(0, index);
  }
}
