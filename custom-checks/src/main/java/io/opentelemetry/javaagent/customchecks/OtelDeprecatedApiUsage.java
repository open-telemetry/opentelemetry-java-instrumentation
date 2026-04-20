/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.customchecks;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import javax.annotation.Nullable;

/**
 * Error Prone check that detects usage of deprecated APIs.
 *
 * <p>This is similar to javac's -Xlint:deprecation but properly honors {@code @SuppressWarnings}
 * (including on import statements, which javac doesn't support with --release 8 due to <a
 * href="https://bugs.openjdk.org/browse/JDK-8032211">JDK-8032211</a>).
 *
 * <p>Can be suppressed with {@code @SuppressWarnings("deprecation")}.
 */
@AutoService(BugChecker.class)
@BugPattern(
    summary = "Use of deprecated API",
    severity = ERROR,
    altNames = "deprecation", // so it can be suppressed with @SuppressWarnings("deprecation")
    suppressionAnnotations = SuppressWarnings.class)
public class OtelDeprecatedApiUsage extends BugChecker
    implements BugChecker.MethodInvocationTreeMatcher,
        BugChecker.NewClassTreeMatcher,
        BugChecker.MemberSelectTreeMatcher,
        BugChecker.MemberReferenceTreeMatcher,
        BugChecker.IdentifierTreeMatcher {

  private static final long serialVersionUID = 1L;

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    Symbol sym = ASTHelpers.getSymbol(tree);
    return checkDeprecated(sym, tree, state);
  }

  @Override
  public Description matchNewClass(NewClassTree tree, VisitorState state) {
    Symbol sym = ASTHelpers.getSymbol(tree);
    return checkDeprecated(sym, tree, state);
  }

  @Override
  public Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
    Symbol sym = ASTHelpers.getSymbol(tree);
    return checkDeprecated(sym, tree, state);
  }

  @Override
  public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
    Symbol sym = ASTHelpers.getSymbol(tree);
    return checkDeprecated(sym, tree, state);
  }

  @Override
  public Description matchIdentifier(IdentifierTree tree, VisitorState state) {
    Symbol sym = ASTHelpers.getSymbol(tree);
    return checkDeprecated(sym, tree, state);
  }

  private Description checkDeprecated(Symbol sym, Tree tree, VisitorState state) {
    if (sym == null) {
      return Description.NO_MATCH;
    }

    // Don't warn on import statements
    if (isInsideImport(state)) {
      return Description.NO_MATCH;
    }

    // Check if the symbol itself is deprecated
    if (!isDeprecated(sym, state)) {
      return Description.NO_MATCH;
    }

    // Don't warn if we're inside a deprecated context (class, method, etc.)
    if (isInsideDeprecatedCode(state)) {
      return Description.NO_MATCH;
    }

    // Don't warn if the deprecated symbol is in the same top-level class (matches javac behavior)
    if (isInSameTopLevelClass(sym, state)) {
      return Description.NO_MATCH;
    }

    return buildDescription(tree).setMessage("Use of deprecated API: " + sym).build();
  }

  private static boolean isInsideImport(VisitorState state) {
    for (Tree tree : state.getPath()) {
      if (tree instanceof ImportTree) {
        return true;
      }
    }
    return false;
  }

  private static boolean isDeprecated(Symbol sym, VisitorState state) {
    // First try the symbol's isDeprecated() method
    if (sym.isDeprecated()) {
      return true;
    }
    // Also check for @Deprecated annotation (some symbols may not have flag set)
    return ASTHelpers.hasAnnotation(sym, "java.lang.Deprecated", state);
  }

  private static boolean isInsideDeprecatedCode(VisitorState state) {
    // Check enclosing elements (method, class) for @Deprecated
    // Skip the first element which is the current node being checked
    boolean first = true;
    for (Tree tree : state.getPath()) {
      if (first) {
        first = false;
        continue;
      }
      Symbol sym = ASTHelpers.getSymbol(tree);
      if (sym != null && isDeprecated(sym, state)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isInSameTopLevelClass(Symbol sym, VisitorState state) {
    // Get the top-level class containing the deprecated symbol
    Symbol.ClassSymbol deprecatedTopLevel = getTopLevelClass(sym);
    if (deprecatedTopLevel == null) {
      return false;
    }

    // Get the top-level class containing the current usage by walking the tree path
    // Skip the first element (the node being checked) to find the enclosing class
    Symbol.ClassSymbol usageTopLevel = null;
    boolean first = true;
    for (Tree tree : state.getPath()) {
      if (first) {
        first = false;
        continue;
      }
      Symbol treeSym = ASTHelpers.getSymbol(tree);
      if (treeSym instanceof Symbol.ClassSymbol classSymbol) {
        usageTopLevel = getTopLevelClass(classSymbol);
        if (usageTopLevel != null) {
          break;
        }
      }
    }
    if (usageTopLevel == null) {
      return false;
    }

    return deprecatedTopLevel.equals(usageTopLevel);
  }

  @Nullable
  private static Symbol.ClassSymbol getTopLevelClass(Symbol sym) {
    Symbol current = sym;
    while (current != null) {
      if (current instanceof Symbol.ClassSymbol classSymbol) {
        Symbol owner = classSymbol.owner;
        // Top-level class is owned by a package
        if (owner instanceof Symbol.PackageSymbol) {
          return classSymbol;
        }
      }
      current = current.owner;
    }
    return null;
  }
}
