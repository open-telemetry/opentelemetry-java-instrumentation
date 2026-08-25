/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The target a Couchbase client was configured with, rendered once from the connection string it
 * was built from.
 *
 * <p>A client configured with a single seed keeps that seed as its address, together with the port
 * the seed named. A client configured with several seeds carries all of them in the address as
 * {@code host,host:port}, and has no port of its own. A client configured with a host that resolves
 * through DNS SRV names that host, which is a single seed without a port, so it is rendered the
 * same way as any other lone seed.
 *
 * <p>A port is reported only when the connection string named one. Couchbase reaches a single node
 * through a different default port for every service, so there is no one port that describes a seed
 * the user left unqualified.
 *
 * <p>Only the seeds are rendered, so the address never contains credentials, a bucket, a path,
 * query parameters, options or a fragment.
 */
public class CouchbaseServerTarget {

  private final String address;
  @Nullable private final Integer port;

  /** A builder rendering the seeds from a connection string without its transport scheme. */
  public static Builder builder() {
    return new Builder();
  }

  private CouchbaseServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  public String getAddress() {
    return address;
  }

  /** The port of a single configured seed, or {@code null} when the target names several. */
  @Nullable
  public Integer getPort() {
    return port;
  }

  /** Collects the seeds a client was configured with into a {@link CouchbaseServerTarget}. */
  public static class Builder {

    private final List<String> hosts = new ArrayList<>();
    private final List<Integer> ports = new ArrayList<>();
    private boolean complete = true;

    private Builder() {}

    /**
     * Adds a configured seed, where a {@code port} of zero means the connection string left the
     * seed unqualified.
     *
     * <p>A seed the driver cannot name drops the whole target, because a partial list of seeds
     * describes a deployment the client was never pointed at.
     */
    public void addSeed(@Nullable String host, int port) {
      String cleaned = clean(host);
      if (cleaned == null) {
        complete = false;
        return;
      }
      hosts.add(cleaned);
      ports.add(port > 0 ? port : 0);
    }

    /** The collected target, or {@code null} when no seed could be rendered. */
    @Nullable
    public CouchbaseServerTarget build() {
      if (!complete || hosts.isEmpty()) {
        return null;
      }
      if (hosts.size() == 1) {
        int port = ports.get(0);
        return new CouchbaseServerTarget(hosts.get(0), port > 0 ? port : null);
      }
      StringBuilder group = new StringBuilder();
      for (int i = 0; i < hosts.size(); i++) {
        if (i > 0) {
          group.append(',');
        }
        appendSeed(group, hosts.get(i), ports.get(i));
      }
      return new CouchbaseServerTarget(group.toString(), null);
    }

    private static void appendSeed(StringBuilder group, String host, int port) {
      // a literal ipv6 address is bracketed so that the port stays unambiguous
      if (host.indexOf(':') >= 0) {
        group.append('[').append(host).append(']');
      } else {
        group.append(host);
      }
      if (port > 0) {
        group.append(':').append(port);
      }
    }

    /**
     * The bare host of a seed, or {@code null} when it names none.
     *
     * <p>The drivers hand over seeds they have already parsed, but the older parsers keep whatever
     * followed the host in the connection string, so anything that describes the client rather than
     * the server it talks to is cut away here.
     */
    @Nullable
    private static String clean(@Nullable String host) {
      if (host == null) {
        return null;
      }
      String cleaned = truncateAt(truncateAt(truncateAt(host.trim(), '/'), '?'), '#');
      int credentialsEnd = cleaned.lastIndexOf('@');
      if (credentialsEnd >= 0) {
        cleaned = cleaned.substring(credentialsEnd + 1);
      }
      if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
        cleaned = cleaned.substring(1, cleaned.length() - 1);
      }
      return cleaned.isEmpty() ? null : cleaned;
    }

    private static String truncateAt(String host, char separator) {
      int index = host.indexOf(separator);
      return index < 0 ? host : host.substring(0, index);
    }
  }
}
