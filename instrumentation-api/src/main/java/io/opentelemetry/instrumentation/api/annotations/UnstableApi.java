/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * A marker for public classes and methods that are not part of the stable API exposed by an
 * artifact. Even if the artifact itself is stable (i.e., it has a version number with no suffix
 * such as {@code -alpha}), the marked API has no stability guarantees. It may be changed in a
 * backwards incompatible manner, such as changing its signature, or removed entirely without any
 * prior warning or period of deprecation. Using the API may also require additional dependency
 * declarations on unstable artifacts.
 *
 * <p>Only use an API marked with {@link UnstableApi} if you are comfortable keeping up with
 * breaking changes. In particular, DO NOT use it in a library that itself has a guarantee of
 * stability, there is no valid use case for it.
 */
@Target({
  ElementType.ANNOTATION_TYPE,
  ElementType.CONSTRUCTOR,
  ElementType.METHOD,
  ElementType.TYPE
})
@Documented
@UnstableApi
public @interface UnstableApi {}
