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

import java.util.Properties;

public class JmxConfig {
  public String serviceUrl;
  public String groovyScript;

  public int intervalSeconds;
  public String exporterType;
  public String exporterEndpoint;

  public String username;
  public String password;

  public String keyStorePath;
  public String keyStorePassword;
  public String keyStoreType;
  public String trustStorePath;
  public String trustStorePassword;
  public String jmxRemoteProfiles;
  public String realm;

  private Properties properties;
  private static final String prefix = "otel.jmx.metrics.";

  public JmxConfig(Properties properties) {
    this.properties = new Properties(properties);
    // command line takes precedence
    this.properties.putAll(System.getProperties());

    serviceUrl = getProperty("service.url", null);
    groovyScript = getProperty("groovy.script", null);
    try {
      intervalSeconds = Integer.parseInt(getProperty("interval.seconds", "10"));
    } catch (NumberFormatException e) {
      throw new ConfigureError("Failed to parse " + prefix + "interval.seconds", e);
    }
    exporterType = getProperty("exporter.type", "logging");
    exporterEndpoint = getProperty("exporter.endpoint", null);
    username = getProperty("username", null);
    password = getProperty("password", null);
    keyStorePath = getProperty("keystore.path", null);
    keyStorePassword = getProperty("keystore.password", null);
    keyStoreType = getProperty("keystore.type", null);
    trustStorePath = getProperty("truststore.path", null);
    trustStorePassword = getProperty("truststore.password", null);
    jmxRemoteProfiles = getProperty("remote.profiles", null);
    realm = getProperty("realm", null);
  }

  public JmxConfig() {
    this(new Properties());
  }

  private String getProperty(String key, String dfault) {
    return properties.getProperty(prefix + key, dfault);
  }

  /**
   * Will determine if parsed config is complete, setting any applicable defaults.
   *
   * @throws ConfigureError - Thrown if a configuration value is missing or invalid.
   */
  public void validate() throws ConfigureError {
    if (isBlank(this.serviceUrl)) {
      throw new ConfigureError("service.url must be specified.");
    }

    if (isBlank(this.groovyScript)) {
      throw new ConfigureError("groovy.script must be specified.");
    }

    if (isBlank(this.exporterEndpoint) && this.exporterType.equalsIgnoreCase("otlp")) {
      throw new ConfigureError("exporter.endpoint must be specified for otlp format.");
    }

    if (this.intervalSeconds < 0) {
      throw new ConfigureError("interval.seconds must be positive.");
    }

    if (this.intervalSeconds == 0) {
      this.intervalSeconds = 10;
    }
  }

  /**
   * Determines if a String is null or without non-whitespace chars.
   *
   * @param s - {@link String} to evaluate
   * @return - if s is null or without non-whitespace chars.
   */
  public static boolean isBlank(String s) {
    if (s == null) {
      return true;
    }
    return s.trim().length() == 0;
  }
}
