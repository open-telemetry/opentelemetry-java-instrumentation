/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.helidon.v4_3;

import static java.util.Collections.emptyIterator;
import static java.util.Collections.emptyList;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.webserver.http.ServerRequest;
import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import java.util.Iterator;
import javax.annotation.Nullable;

enum HelidonRequestGetter implements ExtendedTextMapGetter<ServerRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(@Nullable ServerRequest req) {
    if (req == null) {
      return emptyList();
    }
    return () -> req.headers().stream().map(Header::name).iterator();
  }

  @Override
  public String get(@Nullable ServerRequest carrier, String key) {
    if (carrier == null) {
      return null;
    }

    return carrier.headers().first(HeaderNames.create(key)).orElse(null);
  }

  @Override
  public Iterator<String> getAll(@Nullable ServerRequest carrier, String key) {
    if (carrier == null) {
      return emptyIterator();
    }

    return carrier.headers().values(HeaderNames.create(key)).iterator();
  }
}
