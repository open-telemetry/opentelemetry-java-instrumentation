/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.QueryExp;

/**
 * A class describing a set of MBeans which can be used to collect values for a metric. Objects of
 * this class are immutable.
 */
public class BeanGroup {
  // How to specify the MBean(s)
  @Nullable private final QueryExp queryExp;
  private final List<ObjectName> namePatterns;

  /**
   * Constructor for BeanGroup.
   *
   * @param queryExp the QueryExp to be used to filter results when looking for MBeans
   * @param namePatterns an array of ObjectNames used to look for MBeans; usually they will be
   *     patterns. If multiple patterns are provided, they work as logical OR.
   */
  private BeanGroup(@Nullable QueryExp queryExp, List<ObjectName> namePatterns) {
    this.queryExp = queryExp;
    this.namePatterns = namePatterns;
  }

  public static BeanGroup forSingleBean(String bean) throws MalformedObjectNameException {
    return new BeanGroup(null, Collections.singletonList(new ObjectName(bean)));
  }

  public static BeanGroup forBeans(List<String> beans) throws MalformedObjectNameException {
    List<ObjectName> list = new ArrayList<>();
    for (String name : beans) {
      list.add(new ObjectName(name));
    }
    return new BeanGroup(null, list);
  }

  @Nullable
  QueryExp getQueryExp() {
    return queryExp;
  }

  List<ObjectName> getNamePatterns() {
    return namePatterns;
  }
}
