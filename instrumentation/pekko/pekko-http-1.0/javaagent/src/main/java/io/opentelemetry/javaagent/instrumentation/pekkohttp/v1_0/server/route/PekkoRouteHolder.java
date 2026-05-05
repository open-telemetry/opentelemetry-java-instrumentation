/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pekko.http.javadsl.model.AttributeKey;
import org.apache.pekko.http.scaladsl.model.Uri;

public class PekkoRouteHolder implements ImplicitContextKeyed {
  public static final AttributeKey<PekkoRouteHolder> ATTRIBUTE_KEY =
      AttributeKey.create("opentelemetry-pekko-route", PekkoRouteHolder.class);
  private static final ContextKey<PekkoRouteHolder> KEY = named("opentelemetry-pekko-route");

  private final List<String> paths = new ArrayList<>();
  private Uri.Path lastUnmatchedPath = null;
  private boolean lastWasMatched = false;
  @Nullable private final PekkoRouteHolder parent;
  @Nullable private volatile PekkoRouteHolder override;
  @Nullable private volatile PekkoRouteHolder fallback;

  public static PekkoRouteHolder create() {
    return new PekkoRouteHolder(null);
  }

  public static PekkoRouteHolder get(Context context) {
    return context.get(KEY);
  }

  private PekkoRouteHolder(@Nullable PekkoRouteHolder parent) {
    this.parent = parent;
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
      paths.add(pathToPush);
      lastUnmatchedPath = afterMatch;

      // If there's nothing left to match, set this as the fallback route holder in case
      // pekko cancels the user routes (e.g. in case of a request timeout).
      // If the path is fully matched by multiple PekkoRouteHolders, the latest one wins,
      // which is usually correct but not guaranteed to be.
      if (afterMatch.isEmpty()) {
        PekkoRouteHolder initialRouteHolder = this;
        while (initialRouteHolder.parent != null) {
          initialRouteHolder = initialRouteHolder.parent;
        }
        initialRouteHolder.fallback = this;
      }
    }
    lastWasMatched = true;
  }

  public void didNotMatch() {
    lastWasMatched = false;
  }

  @Nullable
  public String route() {
    if (override != null && override != this) {
      return override.route();
    }
    if (fallback != null && fallback != this) {
      return fallback.route();
    }
    if (!lastWasMatched) {
      return null;
    }
    boolean shouldAddFinalWildcard = lastUnmatchedPath != null && !lastUnmatchedPath.isEmpty();
    Deque<String> routePaths = new ArrayDeque<>();

    for (PekkoRouteHolder routeHolder = this;
        routeHolder != null;
        routeHolder = routeHolder.parent) {

      for (int i = routeHolder.paths.size() - 1; i >= 0; i--) {
        routePaths.addFirst(routeHolder.paths.get(i));
      }
    }
    if (shouldAddFinalWildcard) {
      routePaths.addLast("*");
    }
    return String.join("", routePaths);
  }

  public boolean hasRoute() {
    return override != null || lastWasMatched;
  }

  public PekkoRouteHolder createChild() {
    return new PekkoRouteHolder(this);
  }

  public void setOverride(PekkoRouteHolder override) {
    if (override.override != null) {
      // The given override itself has an override, so just use that one instead
      this.override = override.override;
    } else {
      this.override = override;
    }
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
