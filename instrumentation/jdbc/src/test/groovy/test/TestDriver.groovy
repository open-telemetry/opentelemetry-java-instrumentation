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

package test

import java.sql.Connection
import java.sql.Driver
import java.sql.DriverPropertyInfo
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger

class TestDriver implements Driver {
  @Override
  Connection connect(String url, Properties info) throws SQLException {
    return new TestConnection("connectException=true" == url)
  }

  @Override
  boolean acceptsURL(String url) throws SQLException {
    return false
  }

  @Override
  DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    return new DriverPropertyInfo[0]
  }

  @Override
  int getMajorVersion() {
    return 0
  }

  @Override
  int getMinorVersion() {
    return 0
  }

  @Override
  boolean jdbcCompliant() {
    return false
  }

  @Override
  Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return null
  }
}
