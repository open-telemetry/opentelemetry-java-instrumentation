/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.weblogictest;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * Registers synthetic WebLogic runtime MBeans for exercising the JMX metric rules in public CI.
 *
 * <p>The real WebLogic container test remains the source of truth for compatibility with WebLogic.
 */
public final class WeblogicTestApplication {

  private static final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

  public static void main(String[] args) throws Exception {
    register(
        "com.bea:ServerRuntime=AdminServer,Name=ThreadPoolRuntime,Type=ThreadPoolRuntime",
        values(
            "ExecuteThreadTotalCount",
            24L,
            "ExecuteThreadIdleCount",
            18L,
            "StandbyThreadCount",
            2L,
            "StuckThreadCount",
            0L,
            "QueueLength",
            0L));

    register(
        "com.bea:ServerRuntime=AdminServer,ApplicationRuntime=demo,Name=demo,"
            + "Type=WebAppComponentRuntime",
        values(
            "OpenSessionsCurrentCount",
            3L,
            "SessionsOpenedTotalCount",
            42L,
            "ContextRoot",
            "/demo"));
    register(
        "com.bea:ServerRuntime=AdminServer,ApplicationRuntime=idle,Name=idle,"
            + "Type=WebAppComponentRuntime",
        values(
            "OpenSessionsCurrentCount",
            0L,
            "SessionsOpenedTotalCount",
            0L,
            "ContextRoot",
            "/idle"));

    register(
        "com.bea:ServerRuntime=AdminServer,ApplicationRuntime=demo,"
            + "WebAppComponentRuntime=demo,Name=MainServlet,Type=ServletRuntime",
        values(
            "InvocationTotalCount",
            120L,
            "ExecutionTimeTotal",
            840L,
            "ExecutionTimeHigh",
            75L,
            "ContextPath",
            "/demo"));
    register(
        "com.bea:ServerRuntime=AdminServer,ApplicationRuntime=idle,"
            + "WebAppComponentRuntime=idle,Name=IdleServlet,Type=ServletRuntime",
        values(
            "InvocationTotalCount",
            0L,
            "ExecutionTimeTotal",
            0L,
            "ExecutionTimeHigh",
            0L,
            "ContextPath",
            "/idle"));

    register(
        "com.bea:ServerRuntime=AdminServer,Name=DefaultHttp,Type=ServerChannelRuntime",
        values("BytesReceivedCount", 65000L, "BytesSentCount", 0L));

    register(
        "com.bea:ServerRuntime=AdminServer,Name=ExampleDS,Type=JDBCDataSourceRuntime",
        values(
            "ActiveConnectionsCurrentCount",
            0L,
            "NumAvailable",
            1L,
            "WaitingForConnectionTotal",
            0L));

    register(
        "com.bea:ServerRuntime=AdminServer,Name=JTARuntime,Type=JTARuntime",
        values(
            "ActiveTransactionsTotalCount",
            1L,
            "TransactionTotalCount",
            90L,
            "TransactionCommittedTotalCount",
            85L,
            "TransactionRolledBackAppTotalCount",
            1L,
            "TransactionRolledBackResourceTotalCount",
            1L,
            "TransactionRolledBackSystemTotalCount",
            2L,
            "TransactionRolledBackTimeoutTotalCount",
            1L,
            "TransactionAbandonedTotalCount",
            0L));

    System.out.println("Synthetic WebLogic runtime MBeans registered.");
    Thread.currentThread().join();
  }

  private static void register(String objectName, Map<String, Object> attributes) throws Exception {
    server.registerMBean(new ReadOnlyAttributes(attributes), new ObjectName(objectName));
  }

  private static Map<String, Object> values(Object... entries) {
    Map<String, Object> values = new HashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      values.put((String) entries[i], entries[i + 1]);
    }
    return values;
  }

  private WeblogicTestApplication() {}

  private static final class ReadOnlyAttributes implements DynamicMBean {
    private final Map<String, Object> attributes;
    private final MBeanInfo info;

    private ReadOnlyAttributes(Map<String, Object> attributes) {
      this.attributes = Collections.unmodifiableMap(new HashMap<>(attributes));
      MBeanAttributeInfo[] attributeInfo =
          attributes.entrySet().stream()
              .map(
                  entry ->
                      new MBeanAttributeInfo(
                          entry.getKey(),
                          entry.getValue().getClass().getName(),
                          entry.getKey(),
                          true,
                          false,
                          false))
              .toArray(MBeanAttributeInfo[]::new);
      info =
          new MBeanInfo(
              getClass().getName(),
              "Read-only synthetic WebLogic runtime attributes",
              attributeInfo,
              null,
              null,
              null);
    }

    @Override
    public Object getAttribute(String name) throws AttributeNotFoundException {
      if (!attributes.containsKey(name)) {
        throw new AttributeNotFoundException(name);
      }
      return attributes.get(name);
    }

    @Override
    public AttributeList getAttributes(String[] names) {
      AttributeList result = new AttributeList();
      for (String name : names) {
        if (attributes.containsKey(name)) {
          result.add(new Attribute(name, attributes.get(name)));
        }
      }
      return result;
    }

    @Override
    public void setAttribute(Attribute attribute) {
      throw new UnsupportedOperationException("Read-only");
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
      return new AttributeList();
    }

    @Override
    public Object invoke(String action, Object[] params, String[] signature) {
      return null;
    }

    @Override
    public MBeanInfo getMBeanInfo() {
      return info;
    }
  }
}
