/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import java.util.LinkedList;
import org.apache.pekko.http.javadsl.model.AttributeKey;
import org.apache.pekko.http.scaladsl.model.Uri;

public class PekkoRouteHolder implements ImplicitContextKeyed {
  public static final AttributeKey<PekkoRouteHolder> ATTRIBUTE_KEY =
      AttributeKey.create("opentelemetry-pekko-route", PekkoRouteHolder.class);
  private static final ContextKey<PekkoRouteHolder> KEY = named("opentelemetry-pekko-route");

  private final LinkedList<String> paths = new LinkedList<>();
  private Uri.Path lastUnmatchedPath = null;
  private boolean lastWasMatched = false;
  private final PekkoRouteHolder parent;
  private PekkoRouteHolder override;

  public static PekkoRouteHolder create() {
    return new PekkoRouteHolder(null);
  }

  public static PekkoRouteHolder get(Context context) {
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
      paths.add(pathToPush);
      lastUnmatchedPath = afterMatch;

      // If there's nothing left to match, set this as the fallback route holder in case
      // pekko cancels the user routes (e.g. in case of a request timeout).
      // If the path is fully matched by multiple PekkoRouteHolders, the latest one wins,
      // which is usually correct but not guaranteed to be.
      if (afterMatch.isEmpty()) {
        PekkoFallbackRouteHolder fallback =
            PekkoFallbackRouteHolder.get(Java8BytecodeBridge.currentContext());
        fallback.setFallback(this);
      }
    }
    lastWasMatched = true;
  }

  public void didNotMatch() {
    lastWasMatched = false;
  }

  public String route() {
    if (override != null) {
      return override.route();
    }
    if (!lastWasMatched) {
      return null;
    }
    boolean shouldAddFinalWildcard = lastUnmatchedPath != null && !lastUnmatchedPath.isEmpty();
    int size = shouldAddFinalWildcard ? 1 : 0;
    LinkedList<PekkoRouteHolder> routeHolders = new LinkedList<>();
    for (PekkoRouteHolder routeHolder = this;
        routeHolder != null;
        routeHolder = routeHolder.parent) {
      routeHolders.addFirst(routeHolder);
      for (String path : routeHolder.paths) {
        size += path.length();
      }
    }
    StringBuilder builder = new StringBuilder(size);
    for (PekkoRouteHolder routeHolder : routeHolders) {
      for (String path : routeHolder.paths) {
        builder.append(path);
      }
    }
    if (shouldAddFinalWildcard) {
      builder.append("*");
    }

    return builder.toString();
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

  private PekkoRouteHolder(PekkoRouteHolder parent) {
    this.parent = parent;
  }
}
