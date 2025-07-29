/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package net.bytebuddy.agent.builder;

import static java.util.logging.Level.FINE;

import io.opentelemetry.javaagent.extension.matcher.internal.DelegatingMatcher;
import io.opentelemetry.javaagent.extension.matcher.internal.DelegatingSuperTypeMatcher;
import io.opentelemetry.javaagent.tooling.DefineClassHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder.Default.Transformation;
import net.bytebuddy.matcher.BooleanMatcher;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ErasureMatcher;
import net.bytebuddy.matcher.HasSuperClassMatcher;
import net.bytebuddy.matcher.HasSuperTypeMatcher;
import net.bytebuddy.matcher.NameMatcher;
import net.bytebuddy.matcher.StringMatcher;
import net.bytebuddy.matcher.StringSetMatcher;
import net.bytebuddy.utility.JavaModule;

/** This class is in byte buddy package to get access to package private members and types. */
public class AgentBuilderUtil {
  private static final Logger logger = Logger.getLogger(AgentBuilderUtil.class.getName());

  private static final Field agentBuilderTransformationsField =
      getField(AgentBuilder.Default.class, "transformations");
  private static final Field rawConjunctionMatchersField =
      getField(AgentBuilder.RawMatcher.Conjunction.class, "matchers");
  private static final Field forElementMatcherField =
      getField(AgentBuilder.RawMatcher.ForElementMatchers.class, "typeMatcher");
  private static final Field nameMatcherField = getField(NameMatcher.class, "matcher");
  private static final Field hasSuperClassMatcherField =
      getField(HasSuperClassMatcher.class, "matcher");
  private static final Field hasSuperTypeMatcherField =
      getField(HasSuperTypeMatcher.class, "matcher");
  private static final Field erasureMatcherField = getField(ErasureMatcher.class, "matcher");
  private static final Field conjunctionMatchersField =
      getField(ElementMatcher.Junction.Conjunction.class, "matchers");
  private static final Field stringMatcherValueField = getField(StringMatcher.class, "value");
  private static final Field stringMatcherModeField = getField(StringMatcher.class, "mode");
  private static final Field stringSetMatcherValuesField =
      getField(StringSetMatcher.class, "values");

  private AgentBuilderUtil() {}

  /**
   * Replaces byte buddy transformer list with a proxy that does not return the transformers that we
   * know are not going to match for currently transformed class.
   */
  public static AgentBuilder optimize(AgentBuilder agentBuilder) {
    try {
      agentBuilder = agentBuilder.with(new TransformContext());

      optimize((AgentBuilder.Default) agentBuilder);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to optimize transformations", exception);
    }
    return agentBuilder;
  }

  private static void optimize(AgentBuilder.Default agentBuilder) throws Exception {
    // class names that have a matcher that matches by name
    Set<String> classNames = new HashSet<>();
    // class names that have a matcher that matches subtypes
    Set<String> superTypeNames = new HashSet<>();
    List<Transformation> unoptimizedTransformations = new ArrayList<>();
    List<Transformation> transformations = agentBuilder.transformations;
    for (Transformation transformation : transformations) {
      AgentBuilder.RawMatcher matcher = transformation.getMatcher();
      // attempt to decompose the matcher and find if it applies to a named class or a subclass
      Result result = inspect(matcher);
      if (result == null) {
        // we were not able to decompose the matcher
        unoptimizedTransformations.add(transformation);
      } else if (result.subtype) {
        superTypeNames.addAll(result.names);
      } else {
        classNames.addAll(result.names);
      }
    }

    List<?> list =
        (List<?>)
            Proxy.newProxyInstance(
                AgentBuilderUtil.class.getClassLoader(),
                new Class<?>[] {List.class},
                (proxy, method, args) -> {
                  String name = TransformContext.getTransformedClassName();
                  // iterator() is the only method we expect to be called on this List
                  if (name != null && "iterator".equals(method.getName())) {
                    // we know that this class is going to be transformed
                    if (classNames.contains(name) || superTypeNames.contains(name)) {
                      return transformations.iterator();
                    }
                    // we already know that loading this class is going to fail, no need to
                    // transform it
                    if (DefineClassHandler.isFailedClass(name)) {
                      return Collections.emptyIterator();
                    }
                    Set<String> loadingSuperTypes = DefineClassHandler.getSuperTypes();
                    // super types set should contain at least java.lang.Object if this set is
                    // empty something unexpected has happened, run all transformations
                    if (loadingSuperTypes.isEmpty()) {
                      return transformations.iterator();
                    }
                    for (String className : loadingSuperTypes) {
                      // we know that this class is going to be transformed
                      if (superTypeNames.contains(className)) {
                        return transformations.iterator();
                      }
                    }

                    // apply only the transformations that we can't decompose
                    return unoptimizedTransformations.iterator();
                  }

                  return method.invoke(transformations, args);
                });

    agentBuilderTransformationsField.set(agentBuilder, list);
  }

  @Nullable
  private static Result inspect(AgentBuilder.RawMatcher matcher) throws Exception {
    if (matcher instanceof AgentBuilder.RawMatcher.Conjunction) {
      List<AgentBuilder.RawMatcher> matchers = getDelegateMatchers(matcher);
      if (!matchers.isEmpty()) {
        // with our current matchers we only need to inspect the first element of the conjunction
        return inspect(matchers.get(0));
      }
    } else if (matcher instanceof AgentBuilder.RawMatcher.ForElementMatchers) {
      ElementMatcher<?> elementMatcher =
          getDelegateMatcher((AgentBuilder.RawMatcher.ForElementMatchers) matcher);
      Result result = inspect(elementMatcher);
      if (result == null && logger.isLoggable(FINE) && shouldLog(elementMatcher)) {
        logger.log(Level.FINE, "Could not decompose matcher {0}", elementMatcher);
      }
      return result;
    }

    return null;
  }

  @Nullable
  private static Result inspect(ElementMatcher<?> matcher) throws Exception {
    if (matcher instanceof DelegatingMatcher) {
      Result result = inspect(((DelegatingMatcher) matcher).getDelegate());
      if (matcher instanceof DelegatingSuperTypeMatcher) {
        return Result.subtype(result);
      }
      return result;
    } else if (matcher instanceof HasSuperClassMatcher) {
      return Result.subtype(inspect(getDelegateMatcher((HasSuperClassMatcher<?>) matcher)));
    } else if (matcher instanceof HasSuperTypeMatcher) {
      return Result.subtype(inspect(getDelegateMatcher((HasSuperTypeMatcher<?>) matcher)));
    } else if (matcher instanceof ErasureMatcher) {
      return inspect(getDelegateMatcher((ErasureMatcher<?>) matcher));
    } else if (matcher instanceof NameMatcher) {
      return inspectNameMatcher((NameMatcher<?>) matcher);
    } else if (matcher instanceof ElementMatcher.Junction.Conjunction) {
      List<ElementMatcher<?>> matchers =
          getDelegateMatchers((ElementMatcher.Junction.Conjunction<?>) matcher);
      for (ElementMatcher<?> elementMatcher : matchers) {
        Result result = inspect(elementMatcher);
        if (result != null) {
          return result;
        }
      }
    }

    return null;
  }

  @Nullable
  private static Result inspectNameMatcher(NameMatcher<?> nameMatcher) throws Exception {
    ElementMatcher<?> matcher = getDelegateMatcher(nameMatcher);
    if (matcher instanceof StringMatcher) {
      String value = getStringMatcherValue((StringMatcher) matcher);
      return Result.named(value);
    } else if (matcher instanceof StringSetMatcher) {
      Set<String> value = getStringSetMatcherValue((StringSetMatcher) matcher);
      return Result.named(value);
    }

    return null;
  }

  private static class Result {
    final Set<String> names = new HashSet<>();
    // true if matcher matches based on type hierarchy
    // false if matcher matches based on type name
    final boolean subtype;

    private Result(boolean subtype) {
      this.subtype = subtype;
    }

    private Result() {
      this(false);
    }

    @Nullable
    static Result subtype(@Nullable Result value) {
      if (value == null) {
        return null;
      }

      Result result = new Result(true);
      result.names.addAll(value.names);
      return result;
    }

    @Nullable
    static Result named(@Nullable String value) {
      if (value == null) {
        return null;
      }
      Result result = new Result();
      result.names.add(value);
      return result;
    }

    @Nullable
    static Result named(@Nullable Set<String> value) {
      if (value == null || value.isEmpty()) {
        return null;
      }
      Result result = new Result();
      result.names.addAll(value);
      return result;
    }

    @Override
    public String toString() {
      return (subtype ? "subtype of " : "named ") + names;
    }
  }

  private static ElementMatcher<?> getDelegateMatcher(
      AgentBuilder.RawMatcher.ForElementMatchers matcher) throws Exception {
    return (ElementMatcher<?>) forElementMatcherField.get(matcher);
  }

  private static ElementMatcher<?> getDelegateMatcher(NameMatcher<?> matcher) throws Exception {
    return (ElementMatcher<?>) nameMatcherField.get(matcher);
  }

  private static ElementMatcher<?> getDelegateMatcher(HasSuperClassMatcher<?> matcher)
      throws Exception {
    return (ElementMatcher<?>) hasSuperClassMatcherField.get(matcher);
  }

  private static ElementMatcher<?> getDelegateMatcher(HasSuperTypeMatcher<?> matcher)
      throws Exception {
    return (ElementMatcher<?>) hasSuperTypeMatcherField.get(matcher);
  }

  private static ElementMatcher<?> getDelegateMatcher(ErasureMatcher<?> matcher) throws Exception {
    return (ElementMatcher<?>) erasureMatcherField.get(matcher);
  }

  @SuppressWarnings("unchecked")
  private static List<AgentBuilder.RawMatcher> getDelegateMatchers(AgentBuilder.RawMatcher matcher)
      throws Exception {
    return (List<AgentBuilder.RawMatcher>) rawConjunctionMatchersField.get(matcher);
  }

  @SuppressWarnings("unchecked")
  private static List<ElementMatcher<?>> getDelegateMatchers(
      ElementMatcher.Junction.Conjunction<?> matcher) throws Exception {
    return (List<ElementMatcher<?>>) conjunctionMatchersField.get(matcher);
  }

  /**
   * @return the value given string matcher matches when matcher mode is
   *     StringMatcher.Mode.EQUALS_FULLY, null otherwise
   */
  @Nullable
  private static String getStringMatcherValue(StringMatcher matcher) throws Exception {
    String value = (String) stringMatcherValueField.get(matcher);
    StringMatcher.Mode mode = (StringMatcher.Mode) stringMatcherModeField.get(matcher);
    return mode == StringMatcher.Mode.EQUALS_FULLY ? value : null;
  }

  @SuppressWarnings("unchecked")
  private static Set<String> getStringSetMatcherValue(StringSetMatcher matcher) throws Exception {
    return (Set<String>) stringSetMatcherValuesField.get(matcher);
  }

  private static Field getField(Class<?> clazz, String name) {
    try {
      Field field = clazz.getDeclaredField(name);
      field.setAccessible(true);
      return field;
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean shouldLog(ElementMatcher<?> elementMatcher) {
    return !(elementMatcher instanceof BooleanMatcher);
  }

  private static class TransformContext extends AgentBuilder.Listener.Adapter {
    private static final ThreadLocal<String> transformedName = new ThreadLocal<>();

    @Nullable
    static String getTransformedClassName() {
      return transformedName.get();
    }

    @Override
    public void onDiscovery(
        String typeName,
        @Nullable ClassLoader classLoader,
        @Nullable JavaModule module,
        boolean loaded) {
      if (classLoader != null) {
        transformedName.set(typeName);
      }
    }

    @Override
    public void onError(
        String typeName,
        @Nullable ClassLoader classLoader,
        @Nullable JavaModule module,
        boolean loaded,
        Throwable throwable) {
      transformedName.remove();
    }

    @Override
    public void onComplete(
        String typeName,
        @Nullable ClassLoader classLoader,
        @Nullable JavaModule module,
        boolean loaded) {
      transformedName.remove();
    }
  }
}
