package datadog.trace.agent.jmxfetch;

import static org.datadog.jmxfetch.AppConfig.ACTION_COLLECT;

import com.google.common.collect.ImmutableList;
import datadog.trace.api.Config;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.datadog.jmxfetch.App;
import org.datadog.jmxfetch.AppConfig;
import org.datadog.jmxfetch.reporter.ReporterFactory;

@Slf4j
public class JMXFetch {

  public static final ImmutableList<String> DEFAULT_CONFIGS =
      ImmutableList.of("jmxfetch-config.yaml");

  private static final int SLEEP_AFTER_JMXFETCH_EXITS = 5000;

  private static final String UNIX_DOMAIN_SOCKET_PREFIX = "unix://";

  public static final void run() {
    run(Config.get());
  }

  // This is used by tests
  private static void run(final Config config) {
    if (!config.isJmxFetchEnabled()) {
      log.info("JMXFetch is disabled");
      return;
    }

    if (!log.isDebugEnabled()
        && System.getProperty("org.slf4j.simpleLogger.log.org.datadog.jmxfetch") == null) {
      // Reduce noisiness of jmxfetch logging.
      System.setProperty("org.slf4j.simpleLogger.log.org.datadog.jmxfetch", "warn");
    }

    final String jmxFetchConfigDir = config.getJmxFetchConfigDir();
    final List<String> jmxFetchConfigs = config.getJmxFetchConfigs();
    final List<String> internalMetricsConfigs = getInternalMetricFiles();
    final List<String> metricsConfigs = config.getJmxFetchMetricsConfigs();
    final Integer checkPeriod = config.getJmxFetchCheckPeriod();
    final Integer refreshBeansPeriod = config.getJmxFetchRefreshBeansPeriod();
    final Map<String, String> globalTags = config.getMergedJmxTags();
    final String reporter = getReporter(config);
    final String logLocation = getLogLocation();
    final String logLevel = getLogLevel();

    log.info(
        "JMXFetch config: {} {} {} {} {} {} {} {} {} {}",
        jmxFetchConfigDir,
        jmxFetchConfigs,
        internalMetricsConfigs,
        metricsConfigs,
        checkPeriod,
        refreshBeansPeriod,
        globalTags,
        reporter,
        logLocation,
        logLevel);

    final AppConfig.AppConfigBuilder configBuilder =
        AppConfig.builder()
            .action(ImmutableList.of(ACTION_COLLECT))
            // App should be run as daemon otherwise CLI apps would not exit once main method exits.
            .daemon(true)
            .confdDirectory(jmxFetchConfigDir)
            .yamlFileList(jmxFetchConfigs)
            .targetDirectInstances(true)
            .instanceConfigResources(DEFAULT_CONFIGS)
            .metricConfigResources(internalMetricsConfigs)
            .metricConfigFiles(metricsConfigs)
            .refreshBeansPeriod(refreshBeansPeriod)
            .globalTags(globalTags)
            .reporter(ReporterFactory.getReporter(reporter));

    if (checkPeriod != null) {
      configBuilder.checkPeriod(checkPeriod);
    }
    final AppConfig appConfig = configBuilder.build();

    final Thread thread =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                while (true) {
                  try {
                    final int result = App.run(appConfig);
                    log.error("jmx collector exited with result: " + result);
                  } catch (final Exception e) {
                    log.error("Exception in jmx collector thread", e);
                  }
                  try {
                    Thread.sleep(SLEEP_AFTER_JMXFETCH_EXITS);
                  } catch (final InterruptedException e) {
                    // It looks like JMXFetch itself eats up InterruptedException, so we will do
                    // same here for consistency
                    log.error("JMXFetch was interupted, ignoring", e);
                  }
                }
              }
            });
    thread.setName("dd-jmx-collector");
    thread.setDaemon(true);
    thread.start();
  }

  private static String getReporter(final Config config) {
    if (Config.LOGGING_WRITER_TYPE.equals(config.getWriterType())) {
      // If logging writer is enabled then also enable console reporter in JMXFetch
      return "console";
    }

    String host =
        config.getJmxFetchStatsdHost() == null
            ? config.getAgentHost()
            : config.getJmxFetchStatsdHost();
    int port = config.getJmxFetchStatsdPort();

    if (host.startsWith(UNIX_DOMAIN_SOCKET_PREFIX)) {
      host = host.substring(UNIX_DOMAIN_SOCKET_PREFIX.length());
      // Port equal to zero tells the java dogstatsd client to use UDS
      port = 0;
    }
    return "statsd:" + host + ":" + port;
  }

  private static List<String> getInternalMetricFiles() {
    try {
      final InputStream metricConfigsStream =
          JMXFetch.class.getResourceAsStream("metricconfigs.txt");
      if (metricConfigsStream == null) {
        log.debug("metricconfigs not found. returning empty set");
        return Collections.emptyList();
      } else {
        final String configs = IOUtils.toString(metricConfigsStream, StandardCharsets.UTF_8);
        final String[] split = configs.split("\n");
        final List<String> result = new ArrayList<>(split.length);
        final SortedSet<String> integrationName = new TreeSet<>();
        for (final String config : split) {
          integrationName.clear();
          integrationName.add(config.replace(".yaml", ""));
          if (Config.get().isJmxFetchIntegrationEnabled(integrationName, false)) {
            final URL resource = JMXFetch.class.getResource("metricconfigs/" + config);
            result.add(resource.getPath().split("\\.jar!/")[1]);
          }
        }
        return result;
      }
    } catch (final IOException e) {
      log.debug("error reading metricconfigs. returning empty set", e);
      return Collections.emptyList();
    }
  }

  private static String getLogLocation() {
    return System.getProperty("org.slf4j.simpleLogger.logFile", "System.err");
  }

  private static String getLogLevel() {
    return System.getProperty("org.slf4j.simpleLogger.defaultLogLevel", "info").toUpperCase();
  }
}
