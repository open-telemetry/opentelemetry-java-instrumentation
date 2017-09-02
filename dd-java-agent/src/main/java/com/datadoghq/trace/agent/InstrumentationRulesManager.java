package com.datadoghq.trace.agent;

import com.google.common.collect.Sets;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jboss.byteman.agent.Retransformer;

/**
 * This manager is loaded at pre-main. It loads all the scripts contained in all the 'oatrules.btm'
 * resource files.
 */
@Slf4j
public class InstrumentationRulesManager {

  private static final String INTEGRATION_RULES = "integration-rules.btm";

  private final Retransformer transformer;
  private final TracingAgentConfig config;
  private final AgentRulesManager agentRulesManager;

  private final Set<ClassLoader> initializedClassloaders = Sets.newConcurrentHashSet();

  public InstrumentationRulesManager(
      Retransformer trans, TracingAgentConfig config, AgentRulesManager agentRulesManager) {
    this.transformer = trans;
    this.config = config;
    this.agentRulesManager = agentRulesManager;

    if (InstrumentationChecker.isClassPresent(
        "org.springframework.boot.loader.LaunchedURLClassLoader",
        ClassLoader.getSystemClassLoader())) {
      log.info(
          "Running in the context of a Spring Boot executable jar.  Deferring rule loading to run in the LaunchedURLClassLoader.");
      agentRulesManager.loadRules("spring-boot-rule.btm", ClassLoader.getSystemClassLoader());
    } else {
      initialize(ClassLoader.getSystemClassLoader());
    }
  }

  public static void registerClassLoad(Object obj) {
    log.info("Calling initialize with {}", obj);
    ClassLoader cl;
    if (obj instanceof ClassLoader) {
      cl = (ClassLoader) obj;
    } else {
      cl = obj.getClass().getClassLoader();
    }

    AgentRulesManager.INSTANCE.instrumentationRulesManager.initialize(cl);
  }

  /**
   * This method is separated out from initialize to allow Spring Boot's LaunchedURLClassLoader to
   * call it once it is loaded.
   *
   * @param classLoader
   */
  public void initialize(ClassLoader classLoader) {
    synchronized (classLoader) {
      if (initializedClassloaders.contains(classLoader)) {
        return;
      }
      initializedClassloaders.add(classLoader);
    }

    final List<String> loadedScripts = agentRulesManager.loadRules(INTEGRATION_RULES, classLoader);

    //Check if some rules have to be uninstalled
    final List<String> uninstallScripts = InstrumentationChecker.getUnsupportedRules(classLoader);
    if (config != null) {
      final List<String> disabledInstrumentations = config.getDisabledInstrumentations();
      if (disabledInstrumentations != null && !disabledInstrumentations.isEmpty()) {
        uninstallScripts.addAll(disabledInstrumentations);
      }
    }

    try {
      uninstallScripts(loadedScripts, uninstallScripts);
    } catch (Exception e) {
      log.warn("Error uninstalling scripts", e);
    }
  }

  /**
   * Uninstall some scripts from a list of patterns. All the rules that contain the pattern will be
   * uninstalled
   *
   * @param patterns not case sensitive (eg. "mongo", "apache http", "elasticsearch", etc...])
   */
  private void uninstallScripts(final List<String> installedScripts, final List<String> patterns)
      throws Exception {
    final Set<String> rulesToRemove = new HashSet<>();

    for (final String strPattern : patterns) {
      final Pattern pattern = Pattern.compile("(?i)RULE [^\n]*" + strPattern + "[^\n]*\n");
      for (final String loadedScript : installedScripts) {
        final Matcher matcher = pattern.matcher(loadedScript);
        while (matcher.find()) {
          rulesToRemove.add(matcher.group());
        }
      }
    }

    if (!rulesToRemove.isEmpty()) {
      final StringWriter sw = new StringWriter();
      try (PrintWriter pr = new PrintWriter(sw)) {
        transformer.removeScripts(new ArrayList<>(rulesToRemove), pr);
      }
      log.info("Uninstall rule scripts: {}", rulesToRemove.toString());
    }
  }
}
