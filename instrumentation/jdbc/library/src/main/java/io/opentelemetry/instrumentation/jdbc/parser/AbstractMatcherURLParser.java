/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2017-2021 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.opentelemetry.instrumentation.jdbc.parser;

import io.opentelemetry.instrumentation.jdbc.ConnectionInfo;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser based on regular expression
 *
 * @author oburgosm
 * @since 0.2.12
 */
public abstract class AbstractMatcherURLParser implements ConnectionURLParser {

  private final Pattern pattern;

  private final String dbType;

  protected AbstractMatcherURLParser(Pattern pattern, String dbType) {
    this.pattern = pattern;
    this.dbType = dbType;
  }

  /**
   * Useful to modify ConnectionInfo before build
   *
   * @param matcher The matcher to apply. Note that the matcher must have a group named host, and
   *     optionally, a group named port and another named instance
   */
  protected ConnectionInfo.Builder initBuilder(Matcher matcher) {
    String host = matcher.group("host");
    String port = null;
    try {
      port = matcher.group("port");
    } catch (IllegalArgumentException e) {
      // The pattern has no instance port
    }
    ConnectionInfo.Builder builder;
    if (port == null || "".equals(port)) {
      builder = new ConnectionInfo.Builder(host);
    } else {
      builder = new ConnectionInfo.Builder(host, Integer.valueOf(port));
    }
    String instance = ConnectionInfo.UNKNOWN_CONNECTION_INFO.getDbInstance();
    try {
      instance = matcher.group("instance");
      if (instance == null || "".equals(instance)) {
        instance = ConnectionInfo.UNKNOWN_CONNECTION_INFO.getDbInstance();
      }
    } catch (IllegalArgumentException e) {
      // The pattern has no instance group
    }
    return builder.dbType(this.dbType).dbInstance(instance);
  }

  @Override
  public ConnectionInfo parse(String url) {
    Matcher matcher = this.pattern.matcher(url);
    if (matcher.matches()) {
      return this.initBuilder(matcher).build();
    } else {
      return ConnectionInfo.UNKNOWN_CONNECTION_INFO;
    }
  }
}
