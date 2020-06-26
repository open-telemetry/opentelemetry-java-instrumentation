/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.jdbc;

import static io.opentelemetry.auto.bootstrap.WeakMap.Provider.newWeakMap;

import io.opentelemetry.auto.bootstrap.WeakMap;
import io.opentelemetry.auto.bootstrap.instrumentation.jdbc.DBInfo;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * JDBC instrumentation shares a global map of connection info.
 *
 * <p>Should be injected into the bootstrap classpath.
 */
public class JDBCMaps {
  public static final WeakMap<Connection, DBInfo> connectionInfo = newWeakMap();
  public static final WeakMap<PreparedStatement, String> preparedStatements = newWeakMap();
}
