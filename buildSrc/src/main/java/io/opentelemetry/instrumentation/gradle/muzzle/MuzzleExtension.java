/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle.muzzle;

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;

public abstract class MuzzleExtension {

  private final ObjectFactory objectFactory;

  @Inject
  public MuzzleExtension(ObjectFactory objectFactory) {
    this.objectFactory = objectFactory;
  }

  public abstract ListProperty<MuzzleDirective> getDirectives();

  public void pass(Action<? super MuzzleDirective> action) {
    MuzzleDirective pass = objectFactory.newInstance(MuzzleDirective.class);
    action.execute(pass);
    pass.getAssertPass().set(true);
    getDirectives().add(pass);
  }

  public void fail(Action<? super MuzzleDirective> action) {
    MuzzleDirective fail = objectFactory.newInstance(MuzzleDirective.class);
    action.execute(fail);
    fail.getAssertPass().set(false);
    getDirectives().add(fail);
  }
}
