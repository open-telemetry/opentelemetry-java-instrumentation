package com.datadoghq.trace.agent;

import com.datadoghq.trace.DDTraceAnnotationsInfo;
import com.datadoghq.trace.DDTraceInfo;
import com.datadoghq.trace.resolver.DDTracerFactory;
import com.datadoghq.trace.resolver.FactoryUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jboss.byteman.agent.Retransformer;

@Slf4j
public class AgentRulesManager {

  // Initialize the info classes so they print out their version info:
  private static final String ddJavaAgentVersion = DDJavaAgentInfo.VERSION;
  private static final String ddTraceVersion = DDTraceInfo.VERSION;
  private static final String ddTraceAnnotationsVersion = DDTraceAnnotationsInfo.VERSION;

  private static final String INITIALIZER_RULES = "initializer-rules.btm";

  protected static volatile AgentRulesManager INSTANCE;

  protected final Retransformer transformer;
  protected final TracingAgentConfig agentTracerConfig;
  protected final InstrumentationRulesManager instrumentationRulesManager;
  protected final TraceAnnotationsManager traceAnnotationsManager;

  public AgentRulesManager(Retransformer trans, TracingAgentConfig config) {
    transformer = trans;
    agentTracerConfig = config;
    traceAnnotationsManager = new TraceAnnotationsManager(trans, config);
    instrumentationRulesManager = new InstrumentationRulesManager(trans, config, this);
  }

  /**
   * This method initializes the manager.
   *
   * @param trans The ByteMan retransformer
   */
  public static void initialize(final Retransformer trans) {
    log.debug("Initializing {}", AgentRulesManager.class.getSimpleName());

    TracingAgentConfig config =
        FactoryUtils.loadConfigFromFilePropertyOrResource(
            DDTracerFactory.SYSTEM_PROPERTY_CONFIG_PATH,
            DDTracerFactory.CONFIG_PATH,
            TracingAgentConfig.class);

    log.debug("Configuration: {}", config.toString());

    AgentRulesManager manager = new AgentRulesManager(trans, config);

    INSTANCE = manager;

    manager.loadRules(INITIALIZER_RULES, ClassLoader.getSystemClassLoader());
    manager.traceAnnotationsManager.initialize();
  }

  /**
   * This method loads any OpenTracing Agent rules (integration-rules.btm) found as resources within
   * the supplied classloader.
   *
   * @param classLoader The classloader
   */
  protected List<String> loadRules(String rulesFileName, final ClassLoader classLoader) {
    final List<String> scripts = new ArrayList<>();
    if (transformer == null) {
      log.warn(
          "Attempt to load rules file {} on classloader {} before transformer initialized",
          rulesFileName,
          classLoader == null ? "bootstrap" : classLoader);
      return scripts;
    }

    log.debug("Loading rules with classloader {}", classLoader == null ? "bootstrap" : classLoader);

    final List<String> scriptNames = new ArrayList<>();

    // Load default and custom rules
    try {
      final Enumeration<URL> iter = classLoader.getResources(rulesFileName);
      while (iter.hasMoreElements()) {
        loadRules(iter.nextElement().toURI(), scriptNames, scripts);
      }

      final StringWriter sw = new StringWriter();
      try (PrintWriter writer = new PrintWriter(sw)) {
        try {
          transformer.installScript(scripts, scriptNames, writer);
        } catch (final Exception e) {
          log.warn("Failed to install scripts", e);
        }
      }
      log.debug(sw.toString());
    } catch (IOException | URISyntaxException e) {
      log.warn("Failed to load rules", e);
    }

    log.debug("Rules loaded from {} on classloader {}", rulesFileName, classLoader);
    if (log.isTraceEnabled()) {
      for (final String rule : scripts) {
        log.trace("Loading rule: {}", rule);
      }
    }
    return scripts;
  }

  private static void loadRules(
      final URI uri, final List<String> scriptNames, final List<String> scripts)
      throws IOException {
    log.debug("Load rules from URI uri={} ", uri);

    final StringBuilder str = new StringBuilder();
    try (InputStream is = uri.toURL().openStream()) {

      final byte[] b = new byte[10240];
      int len;
      while ((len = is.read(b)) != -1) {
        str.append(new String(b, 0, len));
      }
    }
    scripts.add(str.toString());
    scriptNames.add(uri.toString());
  }
}
