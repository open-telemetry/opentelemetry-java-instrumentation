package com.datadoghq.trace.agent;

import com.datadoghq.trace.Trace;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
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
 * <p>This class adds rules to instrument all the methods annotated with the @Trace.
 */
@Slf4j
public class TraceAnnotationsManager {
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

  private final Retransformer transformer;
  private final TracingAgentConfig agentTracerConfig;

  public TraceAnnotationsManager(Retransformer trans, TracingAgentConfig config) {
    transformer = trans;
    agentTracerConfig = config;
  }

  /** This method initializes the manager. */
  public void initialize() {
    log.debug("Initializing {}", TraceAnnotationsManager.class.getSimpleName());

    //Check if annotations are enabled
    if (agentTracerConfig != null
        && agentTracerConfig.getEnableCustomAnnotationTracingOver() != null
        && agentTracerConfig.getEnableCustomAnnotationTracingOver().length > 0) {
      loadAnnotationsRules(agentTracerConfig.getEnableCustomAnnotationTracingOver());
    }
  }

  /** Find all the methods annoted with @Trace and inject rules */
  private void loadAnnotationsRules(final String... scannedPackages) {
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
      log.trace("Instrumenting annotated method: {}", method.getName());
    }
    try {
      final StringWriter sw = new StringWriter();
      try (PrintWriter pr = new PrintWriter(sw)) {
        transformer.installScript(
            Arrays.asList(generatedScripts.toString()), Arrays.asList("@Trace annotations"), pr);
      }
      log.debug("Install new rules: \n{}", sw.toString());
    } catch (final Exception e) {
      log.warn("Could not install annotation scripts.", e);
    }

    log.info(
        "Finished annotation scanning in "
            + (System.currentTimeMillis() - cur)
            + " ms. You can accelerate this process by refining the packages you want to scan with `scannedPackages` in the dd-trace.yaml configuration file.");
  }

  private RuleScript createRuleScript(
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
            OpenTracingHelper.class.getName(),
            imports,
            loc,
            ruleText,
            lineNumber,
            "",
            false);
    return ruleScript;
  }

  private String buildSpan(final CtMethod javassistMethod) {
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
