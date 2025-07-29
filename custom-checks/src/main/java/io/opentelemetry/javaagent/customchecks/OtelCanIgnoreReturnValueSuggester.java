/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.customchecks;

import static com.google.errorprone.matchers.Description.NO_MATCH;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.checkreturnvalue.CanIgnoreReturnValueSuggester;
import com.google.errorprone.bugpatterns.checkreturnvalue.CanIgnoreReturnValueSuggesterFactory;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import javax.inject.Inject;

@AutoService(BugChecker.class)
@BugPattern(
    summary =
        "Methods with ignorable return values (including methods that always 'return this') should be annotated with @com.google.errorprone.annotations.CanIgnoreReturnValue",
    severity = BugPattern.SeverityLevel.WARNING)
public class OtelCanIgnoreReturnValueSuggester extends BugChecker
    implements BugChecker.MethodTreeMatcher {

  private static final long serialVersionUID = 1L;

  private final CanIgnoreReturnValueSuggester delegate;

  @Inject
  OtelCanIgnoreReturnValueSuggester(ErrorProneFlags errorProneFlags) {
    delegate =
        CanIgnoreReturnValueSuggesterFactory.createCanIgnoreReturnValueSuggester(errorProneFlags);
  }

  public OtelCanIgnoreReturnValueSuggester() {
    // https://errorprone.info/docs/plugins
    // this constructor is used by ServiceLoader, actual instance will be created with the other
    // constructor
    delegate = null;
  }

  @Override
  public Description matchMethod(MethodTree methodTree, VisitorState visitorState) {
    ClassTree containerClass = findContainingClass(visitorState.getPath());
    if (containerClass.getSimpleName().toString().endsWith("Advice")) {
      return NO_MATCH;
    }
    Description description = delegate.matchMethod(methodTree, visitorState);
    if (description == NO_MATCH) {
      return description;
    }
    return describeMatch(methodTree);
  }

  private static ClassTree findContainingClass(TreePath path) {
    TreePath parent = path.getParentPath();
    while (parent != null && !(parent.getLeaf() instanceof ClassTree)) {
      parent = parent.getParentPath();
    }
    if (parent == null) {
      throw new IllegalStateException(
          "Method is expected to be contained in a class, something must be wrong");
    }
    ClassTree containerClass = (ClassTree) parent.getLeaf();
    return containerClass;
  }
}
