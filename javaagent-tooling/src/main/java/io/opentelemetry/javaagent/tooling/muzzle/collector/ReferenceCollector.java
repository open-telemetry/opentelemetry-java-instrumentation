/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.collector;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;

import com.google.common.base.Strings;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationClassPredicate;
import io.opentelemetry.javaagent.tooling.muzzle.Reference;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.bytebuddy.jar.asm.ClassReader;

/**
 * {@link LinkedHashMap} is used for reference map to guarantee a deterministic order of iteration,
 * so that bytecode generated based on it would also be deterministic.
 *
 * <p>This class is only called at compile time by the {@link MuzzleCodeGenerationPlugin} ByteBuddy
 * plugin.
 */
public class ReferenceCollector {
  private final Map<String, Reference> references = new LinkedHashMap<>();
  private final MutableGraph<String> helperSuperClassGraph = GraphBuilder.directed().build();
  private final Set<String> visitedClasses = new HashSet<>();
  private final InstrumentationClassPredicate instrumentationClassPredicate;

  public ReferenceCollector(Predicate<String> libraryInstrumentationPredicate) {
    this.instrumentationClassPredicate =
        new InstrumentationClassPredicate(libraryInstrumentationPredicate);
  }

  /**
   * If passed {@code resource} path points to an SPI file (either Java {@link
   * java.util.ServiceLoader} or AWS SDK {@code ExecutionInterceptor}) reads the file and adds every
   * implementation as a reference, traversing the graph of classes until a non-instrumentation
   * (external) class is encountered.
   *
   * @param resource path to the resource file, same as in {@link ClassLoader#getResource(String)}
   * @see io.opentelemetry.javaagent.tooling.muzzle.InstrumentationClassPredicate
   */
  public void collectReferencesFromResource(String resource) {
    if (!isSpiFile(resource)) {
      return;
    }

    List<String> spiImplementations = new ArrayList<>();
    try (InputStream stream = getResourceStream(resource)) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8));
      while (reader.ready()) {
        String line = reader.readLine();
        if (!Strings.isNullOrEmpty(line)) {
          spiImplementations.add(line);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Error reading resource " + resource, e);
    }

    visitClassesAndCollectReferences(spiImplementations, false);
  }

  private static final Pattern AWS_SDK_V2_SERVICE_INTERCEPTOR_SPI =
      Pattern.compile("software/amazon/awssdk/services/\\w+(/\\w+)?/execution.interceptors");

  private static final Pattern AWS_SDK_V1_SERVICE_INTERCEPTOR_SPI =
      Pattern.compile("com/amazonaws/services/\\w+(/\\w+)?/request.handler2s");

  private boolean isSpiFile(String resource) {
    return resource.startsWith("META-INF/services/")
        || resource.equals("software/amazon/awssdk/global/handlers/execution.interceptors")
        || resource.equals("com/amazonaws/global/handlers/request.handler2s")
        || AWS_SDK_V2_SERVICE_INTERCEPTOR_SPI.matcher(resource).matches()
        || AWS_SDK_V1_SERVICE_INTERCEPTOR_SPI.matcher(resource).matches();
  }

  /**
   * Traverse a graph of classes starting from {@code adviceClassName} and collect all references to
   * both internal (instrumentation) and external classes.
   *
   * <p>The graph of classes is traversed until a non-instrumentation (external) class is
   * encountered.
   *
   * @param adviceClassName Starting point for generating references.
   * @see io.opentelemetry.javaagent.tooling.muzzle.InstrumentationClassPredicate
   */
  public void collectReferencesFromAdvice(String adviceClassName) {
    visitClassesAndCollectReferences(singleton(adviceClassName), true);
  }

  private void visitClassesAndCollectReferences(
      Collection<String> startingClasses, boolean startsFromAdviceClass) {
    Queue<String> instrumentationQueue = new ArrayDeque<>(startingClasses);
    boolean isAdviceClass = startsFromAdviceClass;

    while (!instrumentationQueue.isEmpty()) {
      String visitedClassName = instrumentationQueue.remove();
      visitedClasses.add(visitedClassName);

      try (InputStream in = getClassFileStream(visitedClassName)) {
        // only start from method bodies for the advice class (skips class/method references)
        ReferenceCollectingClassVisitor cv =
            new ReferenceCollectingClassVisitor(instrumentationClassPredicate, isAdviceClass);
        ClassReader reader = new ClassReader(in);
        reader.accept(cv, ClassReader.SKIP_FRAMES);

        for (Map.Entry<String, Reference> entry : cv.getReferences().entrySet()) {
          String refClassName = entry.getKey();
          Reference reference = entry.getValue();

          // Don't generate references created outside of the instrumentation package.
          if (!visitedClasses.contains(refClassName)
              && instrumentationClassPredicate.isInstrumentationClass(refClassName)) {
            instrumentationQueue.add(refClassName);
          }
          addReference(refClassName, reference);
        }
        collectHelperClasses(
            isAdviceClass, visitedClassName, cv.getHelperClasses(), cv.getHelperSuperClasses());

      } catch (IOException e) {
        throw new IllegalStateException("Error reading class " + visitedClassName, e);
      }

      if (isAdviceClass) {
        isAdviceClass = false;
      }
    }
  }

  private static InputStream getClassFileStream(String className) throws IOException {
    return getResourceStream(Utils.getResourceName(className));
  }

  private static InputStream getResourceStream(String resource) throws IOException {
    URLConnection connection =
        checkNotNull(
                ReferenceCollector.class.getClassLoader().getResource(resource),
                "Couldn't find resource %s",
                resource)
            .openConnection();

    // Since the JarFile cache is not per class loader, but global with path as key, using cache may
    // cause the same instance of JarFile being used for consecutive builds, even if the file has
    // been changed. There is still another cache in ZipFile.Source which checks last modified time
    // as well, so the zip index is not scanned again on every class.
    connection.setUseCaches(false);
    return connection.getInputStream();
  }

  private void addReference(String refClassName, Reference reference) {
    if (references.containsKey(refClassName)) {
      references.put(refClassName, references.get(refClassName).merge(reference));
    } else {
      references.put(refClassName, reference);
    }
  }

  private void collectHelperClasses(
      boolean isAdviceClass,
      String className,
      Set<String> helperClasses,
      Set<String> helperSuperClasses) {
    for (String helperClass : helperClasses) {
      helperSuperClassGraph.addNode(helperClass);
    }
    if (!isAdviceClass) {
      for (String helperSuperClass : helperSuperClasses) {
        helperSuperClassGraph.putEdge(className, helperSuperClass);
      }
    }
  }

  public Map<String, Reference> getReferences() {
    return references;
  }

  // see https://en.wikipedia.org/wiki/Topological_sorting#Kahn's_algorithm
  public List<String> getSortedHelperClasses() {
    MutableGraph<String> dependencyGraph = Graphs.copyOf(Graphs.transpose(helperSuperClassGraph));
    List<String> helperClasses = new ArrayList<>(dependencyGraph.nodes().size());

    Queue<String> helpersWithNoDeps = findAllHelperClassesWithoutDependencies(dependencyGraph);

    while (!helpersWithNoDeps.isEmpty()) {
      String helperClass = helpersWithNoDeps.remove();
      helperClasses.add(helperClass);

      Set<String> dependencies = new HashSet<>(dependencyGraph.successors(helperClass));
      for (String dependency : dependencies) {
        dependencyGraph.removeEdge(helperClass, dependency);
        if (dependencyGraph.predecessors(dependency).isEmpty()) {
          helpersWithNoDeps.add(dependency);
        }
      }
    }

    return helperClasses;
  }

  private static Queue<String> findAllHelperClassesWithoutDependencies(
      Graph<String> dependencyGraph) {
    Queue<String> helpersWithNoDeps = new LinkedList<>();
    for (String helperClass : dependencyGraph.nodes()) {
      if (dependencyGraph.predecessors(helperClass).isEmpty()) {
        helpersWithNoDeps.add(helperClass);
      }
    }
    return helpersWithNoDeps;
  }
}
