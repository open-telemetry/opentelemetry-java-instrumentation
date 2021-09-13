/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty

abstract class MuzzleExtension @Inject constructor(private val objectFactory: ObjectFactory) {

  internal abstract val directives: ListProperty<MuzzleDirective>

  fun pass(action: Action<MuzzleDirective>) {
    val pass = objectFactory.newInstance(MuzzleDirective::class.java)
    action.execute(pass)
    pass.assertPass.set(true)
    directives.add(pass)
  }

  fun fail(action: Action<MuzzleDirective>) {
    val fail = objectFactory.newInstance(MuzzleDirective::class.java)
    action.execute(fail)
    fail.assertPass.set(false)
    directives.add(fail)
  }
}
