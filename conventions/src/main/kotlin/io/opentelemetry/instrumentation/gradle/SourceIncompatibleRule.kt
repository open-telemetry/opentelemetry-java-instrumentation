/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import japicmp.model.JApiBehavior
import japicmp.model.JApiChangeStatus
import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import japicmp.model.JApiCompatibilityChangeType
import japicmp.model.JApiField
import japicmp.model.JApiImplementedInterface
import japicmp.model.JApiMethod
import me.champeau.gradle.japicmp.report.Violation
import me.champeau.gradle.japicmp.report.stdrules.AbstractRecordingSeenMembers

class SourceIncompatibleRule : AbstractRecordingSeenMembers() {
  override fun maybeAddViolation(member: JApiCompatibility): Violation? {
    if (!member.isSourceCompatible && !isNewApiMember(member) && !isGenericAddition(member)) {
      return Violation.error(member, "Not source compatible: $member")
    }
    return null
  }

  private fun isNewApiMember(member: JApiCompatibility): Boolean =
    when (member) {
      is JApiClass -> member.changeStatus == JApiChangeStatus.NEW
      is JApiBehavior -> member.getjApiClass().changeStatus == JApiChangeStatus.NEW
      is JApiField -> member.getjApiClass().changeStatus == JApiChangeStatus.NEW
      is JApiImplementedInterface -> member.changeStatus == JApiChangeStatus.NEW
      else -> false
    }

  private fun isGenericAddition(member: JApiCompatibility): Boolean {
    if (
      member.compatibilityChanges.isEmpty() ||
      member.compatibilityChanges.any {
        it.type != JApiCompatibilityChangeType.CLASS_GENERIC_TEMPLATE_CHANGED
      }
    ) {
      return false
    }

    return when (member) {
      is JApiClass ->
        member.genericTemplates.isNotEmpty() &&
          member.genericTemplates.all { it.changeStatus == JApiChangeStatus.NEW }
      is JApiMethod -> member.changeStatus == JApiChangeStatus.NEW
      else -> false
    }
  }
}
