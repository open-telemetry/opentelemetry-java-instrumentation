/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.customchecks;

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.Description.NO_MATCH;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.PrivateConstructorForUtilityClass;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.ClassTree;

@AutoService(BugChecker.class)
@BugPattern(
    summary =
        "Classes which are not intended to be instantiated should be made non-instantiable with a private constructor. This includes utility classes (classes with only static members), and the main class.",
    severity = WARNING)
public class OtelPrivateConstructorForUtilityClass extends BugChecker
    implements BugChecker.ClassTreeMatcher {

  private static final long serialVersionUID = 1L;

  private final PrivateConstructorForUtilityClass delegate =
      new PrivateConstructorForUtilityClass();

  @Override
  public Description matchClass(ClassTree tree, VisitorState state) {
    if (tree.getSimpleName().toString().endsWith("Advice")) {
      return NO_MATCH;
    }
    Description description = delegate.matchClass(tree, state);
    if (description == NO_MATCH) {
      return description;
    }
    return describeMatch(tree);
  }
}
