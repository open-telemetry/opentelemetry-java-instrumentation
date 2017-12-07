package com.datadoghq.agent;

import com.datadoghq.trace.DDTraceAnnotationsInfo;
import com.datadoghq.trace.DDTraceInfo;
import com.datadoghq.trace.resolver.DDTracerFactory;
import com.datadoghq.trace.resolver.FactoryUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgentRulesManager {

  // Initialize the info classes so they print out their version info:
  private static final String ddJavaAgentVersion = DDJavaAgentInfo.VERSION;
  private static final String ddTraceVersion = DDTraceInfo.VERSION;
  private static final String ddTraceAnnotationsVersion = DDTraceAnnotationsInfo.VERSION;

  private static final String INITIALIZER_RULES = "initializer-rules.btm";

  protected static volatile AgentRulesManager INSTANCE;

  protected final TracingAgentConfig agentTracerConfig;
  protected final InstrumentationRulesManager instrumentationRulesManager;

  public AgentRulesManager(final TracingAgentConfig config) {
    agentTracerConfig = config;
    instrumentationRulesManager = new InstrumentationRulesManager(config, this);
  }

  /** This method initializes the manager. */
  public static void initialize() {
    log.debug("Initializing {}", AgentRulesManager.class.getSimpleName());

    final TracingAgentConfig config =
        FactoryUtils.loadConfigFromFilePropertyOrResource(
            DDTracerFactory.SYSTEM_PROPERTY_CONFIG_PATH,
            DDTracerFactory.CONFIG_PATH,
            TracingAgentConfig.class);

    log.debug("Configuration: {}", config.toString());

    final AgentRulesManager manager = new AgentRulesManager(config);

    INSTANCE = manager;
  }
}
