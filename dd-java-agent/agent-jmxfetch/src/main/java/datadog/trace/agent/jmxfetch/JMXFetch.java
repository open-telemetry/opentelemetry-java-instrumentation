package datadog.trace.agent.jmxfetch;

import com.google.common.collect.ImmutableList;
import datadog.trace.api.Config;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.App;
import org.datadog.jmxfetch.AppConfig;

@Slf4j
public class JMXFetch {

  public static final ImmutableList<String> DEFAULT_CONFIGS =
      ImmutableList.of("jmxfetch-config.yaml");

  private static final int SLEEP_AFTER_JMXFETCH_EXITS = 5000;

  public static final void run() {

    if (!Config.get().isJmxFetchEnabled()) {
      log.info("JMXFetch is disabled");
      return;
    }

    final List<String> metricsConfigs = Config.get().getJmxFetchMetricsConfigs();
    final Integer checkPeriod = Config.get().getJmxFetchCheckPeriod();
    final Integer refreshBeansPeriod = Config.get().getJmxFetchRefreshBeansPeriod();
    final String reporter = getReporter();
    final String logLocation = getLogLocation();
    final String logLevel = getLogLevel();

    log.error(
        "JMXFetch config: {} {} {} {} {} {}",
        metricsConfigs,
        checkPeriod,
        refreshBeansPeriod,
        reporter,
        logLocation,
        logLevel);
    final AppConfig config =
        AppConfig.create(
            DEFAULT_CONFIGS,
            metricsConfigs,
            checkPeriod,
            refreshBeansPeriod,
            reporter,
            logLocation,
            logLevel);

    final Thread thread =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                while (true) {
                  try {
                    final int result = App.run(config);
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

  private static String getReporter() {
    final Config config = Config.get();
    if (Config.LOGGING_WRITER_TYPE.equals(config.getWriterType())) {
      // If logging writer is enabled then also enable console reporter in JMXFetch
      return "console";
    }

    final String host =
        config.getJmxFetchStatsdHost() == null
            ? config.getAgentHost()
            : config.getJmxFetchStatsdHost();
    return "statsd:" + host + ":" + config.getJmxFetchStatsdPort();
  }

  private static String getLogLocation() {
    return System.getProperty("org.slf4j.simpleLogger.logFile", "System.err");
  }

  private static String getLogLevel() {
    return System.getProperty("org.slf4j.simpleLogger.defaultLogLevel", "info").toUpperCase();
  }
}
