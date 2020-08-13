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

package io.opentelemetry.instrumentation.jmxmetrics;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.Provider;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class JmxClient {
  private static final Logger logger = Logger.getLogger(JmxClient.class.getName());

  private final JMXServiceURL url;
  private final String username;
  private final String password;
  private final String realm;
  private final String jmxRemoteProfiles;
  @Nullable private JMXConnector jmxConn;

  JmxClient(JmxConfig config) throws MalformedURLException {
    setSystemProperties(config);
    this.url = new JMXServiceURL(config.serviceUrl);
    this.username = config.username;
    this.password = config.password;
    this.realm = config.realm;
    this.jmxRemoteProfiles = config.jmxRemoteProfiles;
  }

  private static void setSystemProperties(JmxConfig config) {
    if (!JmxConfig.isBlank(config.keyStorePath)) {
      System.setProperty("javax.net.ssl.keyStore", config.keyStorePath);
    }
    if (!JmxConfig.isBlank(config.keyStorePassword)) {
      System.setProperty("javax.net.ssl.keyStorePassword", config.keyStorePassword);
    }
    if (!JmxConfig.isBlank(config.keyStoreType)) {
      System.setProperty("javax.net.ssl.keyStoreType", config.keyStoreType);
    }
    if (!JmxConfig.isBlank(config.trustStorePath)) {
      System.setProperty("javax.net.ssl.trustStore", config.trustStorePath);
    }
    if (!JmxConfig.isBlank(config.trustStorePassword)) {
      System.setProperty("javax.net.ssl.trustStorePassword", config.trustStorePassword);
    }
  }

  private MBeanServerConnection ensureConnected() {
    if (jmxConn != null) {
      try {
        return jmxConn.getMBeanServerConnection();
      } catch (IOException e) {
        // Attempt to connect with authentication below.
      }
    }
    try {
      Map<String, Object> env = new HashMap<>();
      if (!JmxConfig.isBlank(username)) {
        env.put(JMXConnector.CREDENTIALS, new String[] {this.username, this.password});
      }
      try {
        // Not all supported versions of Java contain this Provider
        Class klass = Class.forName("com.sun.security.sasl.Provider");
        Provider provider = (Provider) klass.getDeclaredConstructor().newInstance();
        Security.addProvider(provider);

        env.put("jmx.remote.profiles", this.jmxRemoteProfiles);
        env.put(
            "jmx.remote.sasl.callback.handler",
            new ClientCallbackHandler(this.username, this.password, this.realm));
      } catch (final ReflectiveOperationException e) {
        logger.warning("SASL unsupported in current environment: " + e.getMessage());
      }

      jmxConn = JMXConnectorFactory.connect(url, env);
      return jmxConn.getMBeanServerConnection();
    } catch (IOException e) {
      logger.log(Level.WARNING, "Could not connect to remote JMX server: ", e);
      return null;
    }
  }

  public MBeanServerConnection getConnection() {
    return ensureConnected();
  }

  /**
   * Query the MBean server for a given ObjectName.
   *
   * @param objectName ObjectName to query
   * @return the set of applicable ObjectName instances found by server
   */
  public Set<ObjectName> query(ObjectName objectName) {
    MBeanServerConnection mbsc = ensureConnected();
    if (mbsc == null) {
      return null;
    }

    try {
      return mbsc.queryNames(objectName, null);
    } catch (IOException e) {
      jmxConn = null;
      return null;
    }
  }
}
