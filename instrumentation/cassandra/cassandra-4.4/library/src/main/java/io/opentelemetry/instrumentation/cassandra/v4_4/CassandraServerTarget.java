/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.CONTACT_POINTS;
import static java.util.Collections.emptyList;

import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.internal.core.context.InternalDriverContext;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.DefaultNode;
import com.datastax.oss.driver.internal.core.metadata.MetadataManager;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

final class CassandraServerTarget {

  private static final int DEFAULT_PORT = 9042;

  @Nullable
  static DbServerTarget of(Session session) {
    try {
      DriverContext context = session.getContext();
      if (!(context instanceof InternalDriverContext)) {
        return null;
      }
      MetadataManager metadataManager = ((InternalDriverContext) context).getMetadataManager();
      if (metadataManager.wasImplicitContactPoint()) {
        return null;
      }
      DriverExecutionProfile config = context.getConfig().getDefaultProfile();
      // basic.contact-points has no default, so the single argument lookup would throw when a
      // session names its contact points on the builder alone
      List<String> configuredContactPoints = config.getStringList(CONTACT_POINTS, emptyList());
      DbServerTargetBuilder target = DbServerTarget.builder(DEFAULT_PORT);
      List<ContactPoint> configuredTargets = new ArrayList<>();
      for (String contactPoint : configuredContactPoints) {
        ContactPoint configuredTarget = addContactPoint(target, contactPoint);
        if (configuredTarget != null) {
          configuredTargets.add(configuredTarget);
        }
      }
      boolean hasConfiguredContactPoints = !configuredContactPoints.isEmpty();
      for (DefaultNode node : metadataManager.getContactPoints()) {
        EndPoint endPoint = node.getEndPoint();
        if (endPoint.getClass() != DefaultEndPoint.class) {
          return null;
        }
        SocketAddress address = endPoint.resolve();
        if (!(address instanceof InetSocketAddress)) {
          return null;
        }
        InetSocketAddress inetAddress = (InetSocketAddress) address;
        if (matches(configuredTargets, fromAddress(inetAddress))) {
          continue;
        }
        if (hasConfiguredContactPoints) {
          return null;
        }
        target.addEndpoint(inetAddress);
      }
      return target.build();
    } catch (RuntimeException ignored) {
      // A session that cannot describe its configuration has no stable server target.
      return null;
    }
  }

  @Nullable
  static DbServerTarget of(Session session, Set<EndPoint> programmaticContactPoints) {
    try {
      DriverContext context = session.getContext();
      DriverExecutionProfile config = context.getConfig().getDefaultProfile();
      // basic.contact-points has no default, so the single argument lookup would throw when a
      // session names its contact points on the builder alone
      List<String> configuredContactPoints = config.getStringList(CONTACT_POINTS, emptyList());
      DbServerTargetBuilder target = DbServerTarget.builder(DEFAULT_PORT);
      for (String contactPoint : configuredContactPoints) {
        addContactPoint(target, contactPoint);
      }
      for (EndPoint endPoint : programmaticContactPoints) {
        if (endPoint.getClass() != DefaultEndPoint.class) {
          return null;
        }
        SocketAddress address = endPoint.resolve();
        if (!(address instanceof InetSocketAddress)) {
          return null;
        }
        target.addEndpoint((InetSocketAddress) address);
      }
      return target.build();
    } catch (RuntimeException ignored) {
      // A session that cannot describe its configuration has no stable server target.
      return null;
    }
  }

  @Nullable
  static DbServerTarget of(@Nullable List<String> contactPoints) {
    if (contactPoints == null || contactPoints.isEmpty()) {
      return null;
    }
    DbServerTargetBuilder target = DbServerTarget.builder(DEFAULT_PORT);
    for (String contactPoint : contactPoints) {
      addContactPoint(target, contactPoint);
    }
    return target.build();
  }

  @Nullable
  static DbServerTarget ofAddresses(Collection<InetSocketAddress> contactPoints) {
    DbServerTargetBuilder target = DbServerTarget.builder(DEFAULT_PORT);
    for (InetSocketAddress contactPoint : contactPoints) {
      target.addEndpoint(contactPoint);
    }
    return target.build();
  }

  @Nullable
  private static ContactPoint addContactPoint(
      DbServerTargetBuilder target, @Nullable String contactPoint) {
    if (contactPoint == null) {
      target.addEndpoint((String) null, -1);
      return null;
    }
    int separator = contactPoint.lastIndexOf(':');
    if (separator < 0) {
      target.addEndpoint((String) null, -1);
      return null;
    }
    String host = contactPoint.substring(0, separator);
    if (host.startsWith("[") && host.endsWith("]")) {
      host = host.substring(1, host.length() - 1);
    }
    try {
      int port = Integer.parseInt(contactPoint.substring(separator + 1));
      target.addEndpoint(host, port);
      return new ContactPoint(host, port);
    } catch (NumberFormatException ignored) {
      target.addEndpoint((String) null, -1);
      return null;
    }
  }

  private static boolean matches(List<ContactPoint> configuredTargets, ContactPoint target) {
    for (ContactPoint configuredTarget : configuredTargets) {
      if (configuredTarget.port != target.port) {
        continue;
      }
      if (configuredTarget.host.equals(target.host)) {
        return true;
      }
      InetAddress configuredAddress = numericAddress(configuredTarget.host);
      InetAddress targetAddress = numericAddress(target.host);
      if (configuredAddress != null && configuredAddress.equals(targetAddress)) {
        return true;
      }
    }
    return false;
  }

  private static ContactPoint fromAddress(InetSocketAddress address) {
    String host = address.getHostString();
    if (host.startsWith("[") && host.endsWith("]")) {
      host = host.substring(1, host.length() - 1);
    }
    return new ContactPoint(host, address.getPort());
  }

  @Nullable
  private static InetAddress numericAddress(String address) {
    if (address.indexOf(':') >= 0) {
      try {
        return InetAddress.getByName(address);
      } catch (UnknownHostException ignored) {
        return null;
      }
    }

    byte[] bytes = ipv4Address(address);
    if (bytes == null) {
      return null;
    }
    try {
      return InetAddress.getByAddress(bytes);
    } catch (UnknownHostException ignored) {
      return null;
    }
  }

  @Nullable
  private static byte[] ipv4Address(String address) {
    byte[] bytes = new byte[4];
    int byteIndex = 0;
    int value = 0;
    int digits = 0;
    for (int i = 0; i <= address.length(); i++) {
      char c = i == address.length() ? '.' : address.charAt(i);
      if (c == '.') {
        if (digits == 0 || byteIndex == bytes.length) {
          return null;
        }
        bytes[byteIndex++] = (byte) value;
        value = 0;
        digits = 0;
      } else if (c >= '0' && c <= '9') {
        value = value * 10 + c - '0';
        digits++;
        if (digits > 3 || value > 255) {
          return null;
        }
      } else {
        return null;
      }
    }
    return byteIndex == bytes.length ? bytes : null;
  }

  private CassandraServerTarget() {}

  private static class ContactPoint {

    private final String host;
    private final int port;

    private ContactPoint(String host, int port) {
      this.host = host;
      this.port = port;
    }
  }
}
