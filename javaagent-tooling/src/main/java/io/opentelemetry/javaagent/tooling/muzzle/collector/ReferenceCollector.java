/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.collector;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.opentelemetry.javaagent.tooling.muzzle.InstrumentationClassPredicate.isInstrumentationClass;

import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.muzzle.Reference;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.bytebuddy.jar.asm.ClassReader;

public class ReferenceCollector {
  private final Map<String, Reference> references = new HashMap<>();
  private final MutableGraph<String> helperSuperClassGraph = GraphBuilder.directed().build();
  private final Set<String> visitedClasses = new HashSet<>();

  /**
   * Traverse a graph of classes starting from {@code adviceClassName} and collect all references to
   * both internal (instrumentation) and external classes.
   *
   * <p>The graph of classes is traversed until a non-instrumentation (external) class is
   * encountered.
   *
   * <p>This class is only called at compile time by the {@link MuzzleCodeGenerationPlugin}
   * ByteBuddy plugin.
   *
   * @param adviceClassName Starting point for generating references.
   * @see io.opentelemetry.javaagent.tooling.muzzle.InstrumentationClassPredicate
   */
  public void collectReferencesFrom(String adviceClassName) {
    Queue<String> instrumentationQueue = new ArrayDeque<>();
    instrumentationQueue.add(adviceClassName);

    boolean isAdviceClass = true;

    while (!instrumentationQueue.isEmpty()) {
      String visitedClassName = instrumentationQueue.remove();
      visitedClasses.add(visitedClassName);

      try (InputStream in =
          checkNotNull(
              ReferenceCollector.class
                  .getClassLoader()
                  .getResourceAsStream(Utils.getResourceName(visitedClassName)),
              "Couldn't find class file %s",
              visitedClassName)) {

        // only start from method bodies for the advice class (skips class/method references)
        ReferenceCollectingClassVisitor cv = new ReferenceCollectingClassVisitor(isAdviceClass);
        ClassReader reader = new ClassReader(in);
        reader.accept(cv, ClassReader.SKIP_FRAMES);

        for (Map.Entry<String, Reference> entry : cv.getReferences().entrySet()) {
          String refClassName = entry.getKey();
          Reference reference = entry.getValue();

          // Don't generate references created outside of the instrumentation package.
          if (!visitedClasses.contains(refClassName) && isInstrumentationClass(refClassName)) {
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
