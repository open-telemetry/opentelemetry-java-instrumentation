package com.datadoghq.trace.agent;

import com.datadoghq.trace.Trace;
import com.datadoghq.trace.resolver.DDTracerFactory;
import com.datadoghq.trace.resolver.FactoryUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.Descriptor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.byteman.agent.Location;
import org.jboss.byteman.agent.LocationType;
import org.jboss.byteman.agent.Retransformer;
import org.jboss.byteman.agent.RuleScript;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

/**
 * This manager is loaded at pre-main.
 *
 * <p>It loads all the scripts contained in all the 'oatrules.btm' resource files then instrument
 * all the methods annoted with the @Trace.
 */
@Slf4j
public class TraceAnnotationsManager {

  private static Retransformer transformer;

  private static final String AGENT_RULES = "otarules.btm";

  /**
   * This method initializes the manager.
   *
   * @param trans The ByteMan retransformer
   */
  public static void initialize(final Retransformer trans) throws Exception {
    transformer = trans;
    //Load configuration
    final AgentTracerConfig agentTracerConfig =
        FactoryUtils.loadConfigFromFilePropertyOrResource(
            DDTracerFactory.SYSTEM_PROPERTY_CONFIG_PATH,
            DDTracerFactory.CONFIG_PATH,
            AgentTracerConfig.class);

    final List<String> loadedScripts = loadRules(ClassLoader.getSystemClassLoader());

    //Check if some rules have to be uninstalled
    final List<String> uninstallScripts = InstrumentationChecker.getUnsupportedRules();
    if (agentTracerConfig != null) {
      final List<String> disabledInstrumentations = agentTracerConfig.getDisabledInstrumentations();
      if (disabledInstrumentations != null && !disabledInstrumentations.isEmpty()) {
        uninstallScripts.addAll(disabledInstrumentations);
      }
    }
    uninstallScripts(loadedScripts, uninstallScripts);

    //Check if annotations are enabled
    if (agentTracerConfig != null
        && agentTracerConfig.getEnableCustomAnnotationTracingOver() != null
        && agentTracerConfig.getEnableCustomAnnotationTracingOver().length > 0) {
      loadAnnotationsRules(agentTracerConfig.getEnableCustomAnnotationTracingOver());
    }
  }

  /**
   * Uninstall some scripts from a list of patterns. All the rules that contain the pattern will be
   * uninstalled
   *
   * @param patterns not case sensitive (eg. "mongo", "apache http", "elasticsearch", etc...])
   */
  public static void uninstallScripts(
      final List<String> installedScripts, final List<String> patterns) throws Exception {
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
      log.info(sw.toString());
    }
  }

  /**
   * This method loads any OpenTracing Agent rules (otarules.btm) found as resources within the
   * supplied classloader.
   *
   * @param classLoader The classloader
   */
  public static List<String> loadRules(final ClassLoader classLoader) {
    final List<String> scripts = new ArrayList<>();
    if (transformer == null) {
      log.warn("Attempt to load OpenTracing agent rules before transformer initialized");
      return scripts;
    }

    final List<String> scriptNames = new ArrayList<>();

    // Load default and custom rules
    try {
      final Enumeration<URL> iter = classLoader.getResources(AGENT_RULES);
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
      log.trace(sw.toString());
    } catch (IOException | URISyntaxException e) {
      log.warn("Failed to load OpenTracing agent rules", e);
    }

    log.debug("OpenTracing Agent rules loaded");
    return scripts;
  }

  /** Find all the methods annoted with @Trace and inject rules */
  public static void loadAnnotationsRules(final String... scannedPackages) {

    log.info(
        "Looking for annotations over the following packages: {}", Arrays.asList(scannedPackages));
    final long cur = System.currentTimeMillis();

    final Reflections reflections =
        new Reflections(
            new ConfigurationBuilder()
                .forPackages(scannedPackages)
                .filterInputsBy(
                    new FilterBuilder().includePackage(scannedPackages).include(".*\\.class"))
                .setScanners(new MethodAnnotationsScanner()));

    final Set<Method> methods = reflections.getMethodsAnnotatedWith(Trace.class);

    final StringBuilder generatedScripts = new StringBuilder();
    for (final Method method : methods) {
      try {
        final ClassPool pool = ClassPool.getDefault();
        final CtClass cc = pool.get(method.getDeclaringClass().getCanonicalName());
        final CtMethod javassistMethod = cc.getDeclaredMethod(method.getName());

        //AT ENTRY: child of current case
        final String ruleText = CURRENT_SPAN_EXISTS + buildSpan(javassistMethod) + START;
        RuleScript script =
            createRuleScript(
                "Start Active Span ",
                cc,
                javassistMethod,
                Location.create(LocationType.ENTRY, ""),
                ruleText);
        generatedScripts.append(script).append("\n");

        //AT EXIT
        script =
            createRuleScript(
                "Close span ",
                cc,
                javassistMethod,
                Location.create(LocationType.EXIT, ""),
                EXIT_RULE);
        generatedScripts.append(script).append("\n");

        // AT EXCEPTION EXIT
        script =
            createRuleScript(
                "Close span in error ",
                cc,
                javassistMethod,
                Location.create(LocationType.EXCEPTION_EXIT, ""),
                EXCEPTION_EXIT_RULE);
        generatedScripts.append(script).append("\n");

      } catch (final Exception e) {
        log.warn(
            "Could not create rule for method " + method + ". Proceed to next annoted method.", e);
      }
    }
    try {
      final StringWriter sw = new StringWriter();
      try (PrintWriter pr = new PrintWriter(sw)) {
        transformer.installScript(
            Arrays.asList(generatedScripts.toString()), Arrays.asList("@Trace annotations"), pr);
      }
      log.debug(sw.toString());
    } catch (final Exception e) {
      log.warn("Could not install annotation scripts.", e);
    }

    log.info(
        "Finished annotation scanning in "
            + (System.currentTimeMillis() - cur)
            + " ms. You can accelerate this process by refining the packages you want to scan with `scannedPackages` in the dd-trace.yaml configuration file.");
  }

  private static RuleScript createRuleScript(
      final String ruleNamePrefix,
      final CtClass cc,
      final CtMethod javassistMethod,
      final Location loc,
      final String ruleText) {
    final int lineNumber = javassistMethod.getMethodInfo().getLineNumber(0);
    final String[] imports = new String[0];

    final RuleScript ruleScript =
        new RuleScript(
            ruleNamePrefix + loc + " " + javassistMethod.getLongName(),
            cc.getName(),
            false,
            false,
            javassistMethod.getName() + Descriptor.toString(javassistMethod.getSignature()),
            "io.opentracing.contrib.agent.OpenTracingHelper",
            imports,
            loc,
            ruleText,
            lineNumber,
            "",
            false);
    return ruleScript;
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

  private static final String CURRENT_SPAN_EXISTS = "IF TRUE\n";

  private static final String BUILD_SPAN = "DO\n" + "getTracer().buildSpan(\"";
  private static final String CLOSE_PARENTHESIS = "\")";

  private static final String START = ".startActive();";

  private static final String EXIT_RULE =
      "IF getTracer().activeSpan() != null\n" + "DO\n" + "getTracer().activeSpan().deactivate();\n";

  private static final String EXCEPTION_EXIT_RULE =
      "BIND span:io.opentracing.ActiveSpan = getTracer().activeSpan()\n"
          + "IF span != null\n"
          + "DO\n"
          + "span.setTag(io.opentracing.tag.Tags.ERROR.getKey(),\"true\");\n"
          + "span.deactivate();\n";

  private static String buildSpan(final CtMethod javassistMethod) {
    try {
      final Trace trace = (Trace) javassistMethod.getAnnotation(Trace.class);
      if (trace.operationName() != null & !trace.operationName().isEmpty()) {
        return BUILD_SPAN + trace.operationName() + CLOSE_PARENTHESIS;
      }
    } catch (final Exception e) {
      log.warn(
          "Error when building injection rule on method "
              + javassistMethod
              + ". Fallback on default value.",
          e);
    }
    return BUILD_SPAN + javassistMethod.getName() + CLOSE_PARENTHESIS;
  }
}
