package com.datadoghq.trace.agent;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.jboss.byteman.agent.Retransformer;

/**
 * This manager is loaded at pre-main. It loads all the scripts contained in all the 'oatrules.btm'
 * resource files.
 */
@Slf4j
public class InstrumentationRulesManager {

  private static final String INTEGRATION_RULES = "integration-rules.btm";
  private static final String HELPERS_NAME = "/helpers.jar.zip";

  private final Retransformer transformer;
  private final TracingAgentConfig config;
  private final AgentRulesManager agentRulesManager;
  private final ClassLoaderIntegrationInjector injector;
  private final InstrumentationChecker checker = new InstrumentationChecker();

  private final Set<ClassLoader> initializedClassloaders = Sets.newConcurrentHashSet();

  public InstrumentationRulesManager(
      Retransformer trans, TracingAgentConfig config, AgentRulesManager agentRulesManager) {
    this.transformer = trans;
    this.config = config;
    this.agentRulesManager = agentRulesManager;
    InputStream helpersStream = this.getClass().getResourceAsStream(HELPERS_NAME);
    ZipInputStream stream = new ZipInputStream(helpersStream);
    Map<ZipEntry, byte[]> helperEntries = Maps.newHashMap();
    try {
      ZipEntry entry = stream.getNextEntry();
      while (entry != null) {
        if (entry.isDirectory()) {
          entry = stream.getNextEntry();
          continue;
        }
        // this is a buffer, so the long->int truncation is not an issue.
        ByteArrayOutputStream os = new ByteArrayOutputStream((int) entry.getSize());

        int n;
        byte[] buf = new byte[1024];
        while ((n = stream.read(buf, 0, 1024)) > -1) {
          os.write(buf, 0, n);
        }
        helperEntries.put(entry, os.toByteArray());
        entry = stream.getNextEntry();
      }
    } catch (IOException e) {
      log.error("Error extracting helpers", e);
    }
    injector = new ClassLoaderIntegrationInjector(helperEntries);
  }

  public static void registerClassLoad() {
    log.debug("Register called by class initializer.");
    registerClassLoad(Thread.currentThread().getContextClassLoader());
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

    injector.inject(classLoader);

    final List<String> loadedScripts = agentRulesManager.loadRules(INTEGRATION_RULES, classLoader);

    //Check if some rules have to be uninstalled
    final List<String> uninstallScripts = checker.getUnsupportedRules(classLoader);
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
