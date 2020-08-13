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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JmxMetrics {
  private static final Logger logger = Logger.getLogger(JmxMetrics.class.getName());

  private final Timer timer = new Timer();
  private GroovyRunner runner;

  /**
   * Begins the core metric scraping and reporting loop on configured interval after parsing and
   * binding the configured groovy script and establishing the {@link JmxClient} connection.
   *
   * @param config - {@link JmxConfig} with Groovy script path, JMX connection info, and metric
   *     export options.
   */
  public void start(JmxConfig config) {
    JmxClient jmxClient;
    try {
      jmxClient = new JmxClient(config);
    } catch (MalformedURLException e) {
      throw new ConfigureError("Malformed serviceUrl: ", e);
    }

    runner = new GroovyRunner(config.groovyScript, jmxClient, new GroovyUtils(config));

    timer.scheduleAtFixedRate(
        wrapTimerTask(
            new Runnable() {
              @Override
              public void run() {
                try {
                  runner.run();
                } catch (Throwable e) {
                  logger.log(Level.SEVERE, "Error gathering JMX metrics", e);
                }
              }
            }),
        0,
        config.intervalSeconds * 1000);
    logger.info("Started GroovyRunner.");
  }

  private static TimerTask wrapTimerTask(final Runnable r) {
    return new TimerTask() {
      @Override
      public void run() {
        r.run();
      }
    };
  }

  private void shutdown() {
    logger.info("Shutting down JmxMetrics Groovy runner and exporting final metrics.");
    timer.cancel();
    runner.flush();
  }

  private static JmxConfig getConfigFromArgs(String[] args) {
    if (args.length != 0 && (args.length != 2 || !args[0].equalsIgnoreCase("-config"))) {
      System.out.println(
          "Usage: java io.opentelemetry.extensions.metrics.jmx.JmxMonitor "
              + "-config <path_to_config.properties>");
      System.exit(1);
    }

    Properties props = new Properties();
    if (args.length == 2) {
      try (InputStream is = new FileInputStream(args[1])) {
        props.load(is);
      } catch (IOException e) {
        System.out.println(
            "Failed to read config properties file at '" + args[1] + "': " + e.getMessage());
        System.exit(1);
      }
    }

    return new JmxConfig(props);
  }

  /**
   * Main method to create and run a {@link JmxMetrics} instance.
   *
   * @param args - must be of the form "-config jmx_config_path"
   */
  public static void main(String[] args) {
    JmxConfig config = getConfigFromArgs(args);
    config.validate();

    final JmxMetrics monitor = new JmxMetrics();
    try {
      monitor.start(config);
    } catch (ConfigureError e) {
      logger.severe(e.getMessage());
      System.exit(1);
    }

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                monitor.shutdown();
              }
            });
  }
}
