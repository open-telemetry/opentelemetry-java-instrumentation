/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.jar.asm.ClassReader;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * {@link LinkedHashMap} is used for reference map to guarantee a deterministic order of iteration,
 * so that bytecode generated based on it would also be deterministic.
 *
 * <p>This class is only called at compile time by the {@code MuzzleCodeGenerationPlugin} ByteBuddy
 * plugin.
 */
public final class ReferenceCollector {

  private final Map<String, ClassRef> references = new LinkedHashMap<>();
  private final MutableGraph<String> helperSuperClassGraph = GraphBuilder.directed().build();
  private final InstrumentationContextBuilderImpl contextStoreMappingsBuilder =
      new InstrumentationContextBuilderImpl();
  private final Set<String> visitedClasses = new HashSet<>();
  private final InstrumentationClassPredicate instrumentationClassPredicate;
  private final ClassLoader resourceLoader;

  // only used by tests
  ReferenceCollector(Predicate<String> libraryInstrumentationPredicate) {
    this(libraryInstrumentationPredicate, ReferenceCollector.class.getClassLoader());
  }

  public ReferenceCollector(
      Predicate<String> libraryInstrumentationPredicate, ClassLoader resourceLoader) {
    this.instrumentationClassPredicate =
        new InstrumentationClassPredicate(libraryInstrumentationPredicate);
    this.resourceLoader = resourceLoader;
  }

  /**
   * If passed {@code resource} path points to an SPI file (either Java {@link
   * java.util.ServiceLoader} or AWS SDK {@code ExecutionInterceptor}) reads the file and adds every
   * implementation as a reference, traversing the graph of classes until a non-instrumentation
   * (external) class is encountered.
   *
   * @see InstrumentationClassPredicate
   */
  public void collectReferencesFromResource(HelperResource helperResource) {
    if (!isSpiFile(helperResource.getApplicationPath())) {
      return;
    }

    List<String> spiImplementations = new ArrayList<>();
    try (InputStream stream = getResourceStream(helperResource.getAgentPath())) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8));
      while (reader.ready()) {
        String line = reader.readLine();
        if (!Strings.isNullOrEmpty(line)) {
          spiImplementations.add(line);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Error reading resource " + helperResource.getAgentPath(), e);
    }

    visitClassesAndCollectReferences(spiImplementations, /* startsFromAdviceClass= */ false);
  }

  private static final Pattern AWS_SDK_V2_SERVICE_INTERCEPTOR_SPI =
      Pattern.compile("software/amazon/awssdk/services/\\w+(/\\w+)?/execution.interceptors");

  private static final Pattern AWS_SDK_V1_SERVICE_INTERCEPTOR_SPI =
      Pattern.compile("com/amazonaws/services/\\w+(/\\w+)?/request.handler2s");

  private static boolean isSpiFile(String resource) {
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
   * @see InstrumentationClassPredicate
   */
  public void collectReferencesFromAdvice(String adviceClassName) {
    visitClassesAndCollectReferences(singleton(adviceClassName), /* startsFromAdviceClass= */ true);
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

        for (Map.Entry<String, ClassRef> entry : cv.getReferences().entrySet()) {
          String refClassName = entry.getKey();
          ClassRef reference = entry.getValue();

          // Don't generate references created outside of the instrumentation package.
          if (!visitedClasses.contains(refClassName)
              && instrumentationClassPredicate.isInstrumentationClass(refClassName)) {
            instrumentationQueue.add(refClassName);
          }
          addReference(refClassName, reference);
        }
        collectHelperClasses(
            isAdviceClass, visitedClassName, cv.getHelperClasses(), cv.getHelperSuperClasses());

        contextStoreMappingsBuilder.registerAll(cv.getContextStoreMappings());
      } catch (IOException e) {
        throw new IllegalStateException("Error reading class " + visitedClassName, e);
      }

      if (isAdviceClass) {
        isAdviceClass = false;
      }
    }
  }

  private InputStream getClassFileStream(String className) throws IOException {
    return getResourceStream(Utils.getResourceName(className));
  }

  private InputStream getResourceStream(String resource) throws IOException {
    URLConnection connection =
        Preconditions.checkNotNull(
                resourceLoader.getResource(resource), "Couldn't find resource %s", resource)
            .openConnection();

    // Since the JarFile cache is not per class loader, but global with path as key, using cache may
    // cause the same instance of JarFile being used for consecutive builds, even if the file has
    // been changed. There is still another cache in ZipFile.Source which checks last modified time
    // as well, so the zip index is not scanned again on every class.
    connection.setUseCaches(false);
    return connection.getInputStream();
  }

  private void addReference(String refClassName, ClassRef reference) {
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

  public Map<String, ClassRef> getReferences() {
    return references;
  }

  public void prune() {
    // helper classes that may help another helper class implement an abstract library method
    // must be retained
    // for example if helper class A extends helper class B, and A also implements a library
    // interface L, then B needs to be retained so that it can be used at runtime to verify that A
    // implements all of L's methods.
    // Super types of A that are not also helper classes do not need to be retained because they can
    // be looked up on the classpath at runtime, see HelperReferenceWrapper.create().
    Set<ClassRef> helperClassesParticipatingInLibrarySuperType =
        getHelperClassesParticipatingInLibrarySuperType();

    for (Iterator<ClassRef> i = references.values().iterator(); i.hasNext(); ) {
      ClassRef reference = i.next();
      if (instrumentationClassPredicate.isProvidedByLibrary(reference.getClassName())) {
        // these are the references to library classes which need to be checked at runtime
        continue;
      }
      if (helperClassesParticipatingInLibrarySuperType.contains(reference)) {
        // these need to be kept in order to check that abstract methods are implemented,
        // and to check that declared super class fields are present
        //
        // can at least prune constructors, private, and static methods, since those cannot be used
        // to help implement an abstract library method
        reference
            .getMethods()
            .removeIf(
                method ->
                    method.getName().equals(MethodDescription.CONSTRUCTOR_INTERNAL_NAME)
                        || method.getFlags().contains(Flag.VisibilityFlag.PRIVATE)
                        || method.getFlags().contains(Flag.OwnershipFlag.STATIC));
        continue;
      }
      i.remove();
    }
  }

  private Set<ClassRef> getHelperClassesParticipatingInLibrarySuperType() {
    Set<ClassRef> helperClassesParticipatingInLibrarySuperType = new HashSet<>();
    for (ClassRef reference : getHelperClassesWithLibrarySuperType()) {
      addSuperTypesThatAreAlsoHelperClasses(
          reference.getClassName(), helperClassesParticipatingInLibrarySuperType);
    }
    return helperClassesParticipatingInLibrarySuperType;
  }

  private Set<ClassRef> getHelperClassesWithLibrarySuperType() {
    Set<ClassRef> helperClassesWithLibrarySuperType = new HashSet<>();
    for (ClassRef reference : references.values()) {
      if (instrumentationClassPredicate.isInstrumentationClass(reference.getClassName())
          && hasLibrarySuperType(reference.getClassName())) {
        helperClassesWithLibrarySuperType.add(reference);
      }
    }
    return helperClassesWithLibrarySuperType;
  }

  private void addSuperTypesThatAreAlsoHelperClasses(
      @Nullable String className, Set<ClassRef> superTypes) {
    if (className != null && instrumentationClassPredicate.isInstrumentationClass(className)) {
      ClassRef reference = references.get(className);
      superTypes.add(reference);

      addSuperTypesThatAreAlsoHelperClasses(reference.getSuperClassName(), superTypes);
      // need to keep interfaces too since they may have default methods
      for (String superType : reference.getInterfaceNames()) {
        addSuperTypesThatAreAlsoHelperClasses(superType, superTypes);
      }
    }
  }

  private boolean hasLibrarySuperType(@Nullable String typeName) {
    if (typeName == null || typeName.startsWith("java.")) {
      return false;
    }
    if (instrumentationClassPredicate.isProvidedByLibrary(typeName)) {
      return true;
    }
    ClassRef reference = references.get(typeName);
    if (hasLibrarySuperType(reference.getSuperClassName())) {
      return true;
    }
    for (String type : reference.getInterfaceNames()) {
      if (hasLibrarySuperType(type)) {
        return true;
      }
    }
    return false;
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

  public ContextStoreMappings getContextStoreMappings() {
    return contextStoreMappingsBuilder.build();
  }

  /**
   * Returns a map of {@link io.opentelemetry.javaagent.instrumentation.api.ContextStore} mappings.
   *
   * @deprecated Use {@link #getContextStoreMappings()} instead.
   */
  @Deprecated
  public Map<String, String> getContextStoreClasses() {
    return getContextStoreMappings().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
