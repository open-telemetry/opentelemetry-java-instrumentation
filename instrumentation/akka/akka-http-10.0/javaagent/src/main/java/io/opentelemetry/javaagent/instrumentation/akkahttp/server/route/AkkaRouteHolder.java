/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server.route;

import static io.opentelemetry.context.ContextKey.named;

import akka.http.scaladsl.model.Uri;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import java.util.ArrayDeque;
import java.util.Deque;

public class AkkaRouteHolder implements ImplicitContextKeyed {
  private static final ContextKey<AkkaRouteHolder> KEY = named("opentelemetry-akka-route");

  private StringBuilder route = new StringBuilder();
  private Uri.Path lastUnmatchedPath = null;
  private boolean lastWasMatched = false;
  private final Deque<State> savedStates = new ArrayDeque<>();

  public static Context init(Context context) {
    if (context.get(KEY) != null) {
      return context;
    }
    return context.with(new AkkaRouteHolder());
  }

  public static AkkaRouteHolder get(Context context) {
    return context.get(KEY);
  }

  public void push(Uri.Path beforeMatch, Uri.Path afterMatch, String pathToPush) {
    // Only accept the suggested 'pathToPush' if:
    // - either this is the first match, or
    // - the unmatched part of the path from the previous match is what the current match
    //   acted upon. This avoids pushes from PathMatchers that compose other PathMatchers,
    //   instead only accepting pushes from leaf-nodes in the PathMatcher hierarchy that actually
    //   act on the path.
    // AND:
    // - some part of the path has now been matched by this matcher
    if ((lastUnmatchedPath == null || lastUnmatchedPath.equals(beforeMatch))
        && !afterMatch.equals(beforeMatch)) {
      route.append(pathToPush);
      lastUnmatchedPath = afterMatch;
    }
    lastWasMatched = true;
  }

  public void didNotMatch() {
    lastWasMatched = false;
  }

  public void pushIfNotCompletelyMatched(String pathToPush) {
    if (lastUnmatchedPath != null && !lastUnmatchedPath.isEmpty()) {
      route.append(pathToPush);
    }
  }

  public String route() {
    return lastWasMatched ? route.toString() : null;
  }

  public void save() {
    savedStates.add(new State(lastUnmatchedPath, route));
    route = new StringBuilder(route);
  }

  public void restore() {
    State popped = savedStates.pollLast();
    if (popped != null) {
      lastUnmatchedPath = popped.lastUnmatchedPath;
      route = popped.route;
    }
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }

  private AkkaRouteHolder() {}

  private static class State {
    private final Uri.Path lastUnmatchedPath;
    private final StringBuilder route;

    private State(Uri.Path lastUnmatchedPath, StringBuilder route) {
      this.lastUnmatchedPath = lastUnmatchedPath;
      this.route = route;
    }
  }
}
