/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package muzzle

import io.opentelemetry.javaagent.tooling.muzzle.HelperReferenceWrapper
import io.opentelemetry.javaagent.tooling.muzzle.Reference
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.pool.TypePool
import spock.lang.Shared
import spock.lang.Specification

class HelperReferenceWrapperTest extends Specification {

  @Shared
  def baseHelperClass = new Reference.Builder(HelperReferenceWrapperTest.name + '$BaseHelper')
    .withSuperName(HelperReferenceWrapperTestClasses.AbstractClasspathType.name)
    .withFlag(Reference.Flag.ABSTRACT)
    .withMethod(new Reference.Source[0], new Reference.Flag[0], "foo", Type.VOID_TYPE)
    .withMethod(new Reference.Source[0], [Reference.Flag.ABSTRACT] as Reference.Flag[], "abstract", Type.INT_TYPE)
    .build()

  @Shared
  def helperClass = new Reference.Builder(HelperReferenceWrapperTest.name + '$Helper')
    .withSuperName(baseHelperClass.className)
    .withInterface(HelperReferenceWrapperTestClasses.Interface2.name)
    .withMethod(new Reference.Source[0], new Reference.Flag[0], "bar", Type.VOID_TYPE)
    .build()

  def "should wrap helper types"() {
    given:
    def typePool = TypePool.Default.of(HelperReferenceWrapperTest.classLoader)
    def references = [
      (helperClass.className)    : helperClass,
      (baseHelperClass.className): baseHelperClass
    ]

    when:
    def helperWrapper = new HelperReferenceWrapper.Factory(typePool, references).create(helperClass)

    then:
    with(helperWrapper) { helper ->
      !helper.abstract

      with(helper.methods.toList()) {
        it.size() == 1
        with(it[0]) {
          !it.abstract
          it.name == "bar"
          it.descriptor == "()V"
        }
      }

      helper.hasSuperTypes()
      with(helper.superTypes.toList()) {
        it.size() == 2
        with(it[0]) { baseHelper ->
          baseHelper.abstract

          with(baseHelper.methods.toList()) {
            it.size() == 2
            with(it[0]) {
              !it.abstract
              it.name == 'foo'
              it.descriptor == '()V'
            }
            with(it[1]) {
              it.abstract
              it.name == 'abstract'
              it.descriptor == '()I'
            }
          }

          baseHelper.hasSuperTypes()
          with(baseHelper.superTypes.toList()) {
            it.size() == 1
            with(it[0]) { abstractClasspathType ->
              abstractClasspathType.abstract

              abstractClasspathType.methods.empty

              abstractClasspathType.hasSuperTypes()
              with(abstractClasspathType.superTypes.toList()) {
                it.size() == 2
                with(it[0]) { object ->
                  !object.hasSuperTypes()
                }
                with(it[1]) { interface1 ->
                  interface1.abstract

                  with(interface1.methods.toList()) {
                    it.size() == 1
                    with(it[0]) {
                      it.abstract
                      it.name == "foo"
                      it.descriptor == "()V"
                    }
                  }

                  !interface1.hasSuperTypes()
                  interface1.superTypes.empty
                }
              }
            }
          }
        }
        with(it[1]) { interface2 ->
          interface2.abstract

          with(interface2.methods.toList()) {
            it.size() == 1
            with(it[0]) {
              it.abstract
              it.name == "bar"
              it.descriptor == "()V"
            }
          }

          !interface2.hasSuperTypes()
          interface2.superTypes.empty
        }
      }
    }
  }
}
