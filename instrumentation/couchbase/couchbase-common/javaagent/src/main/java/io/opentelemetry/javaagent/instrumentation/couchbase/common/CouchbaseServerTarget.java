/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

// Multi-seed targets keep each seed's port in the address and therefore have no separate port.
// Unqualified seeds also have no port because each Couchbase service uses a different default.
public class CouchbaseServerTarget {

  private final String address;
  @Nullable private final Integer port;

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

  @Nullable
  public Integer getPort() {
    return port;
  }

  public static class Builder {

    private final List<String> hosts = new ArrayList<>();
    private final List<Integer> ports = new ArrayList<>();
    private boolean complete = true;

    private Builder() {}

    public void addSeed(@Nullable String host, int port) {
      String cleaned = clean(host);
      if (cleaned == null) {
        // A partial seed list describes a target the client was never configured with.
        complete = false;
        return;
      }
      hosts.add(cleaned);
      ports.add(port > 0 ? port : 0);
    }

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

    @Nullable
    private static String clean(@Nullable String host) {
      if (host == null) {
        return null;
      }
      // Older parsers retain credentials and connection-string suffixes in the seed host.
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
