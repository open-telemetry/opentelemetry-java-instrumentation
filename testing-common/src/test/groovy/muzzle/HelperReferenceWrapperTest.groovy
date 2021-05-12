/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package muzzle

import static io.opentelemetry.javaagent.extension.muzzle.Reference.Flag.ManifestationFlag
import static java.util.stream.Collectors.toList

import io.opentelemetry.javaagent.extension.muzzle.Reference
import io.opentelemetry.javaagent.tooling.muzzle.matcher.HelperReferenceWrapper
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.pool.TypePool
import spock.lang.Shared
import spock.lang.Specification

class HelperReferenceWrapperTest extends Specification {

  @Shared
  def baseHelperClass = new Reference.Builder(HelperReferenceWrapperTest.name + '$BaseHelper')
    .withSuperName(HelperReferenceWrapperTestClasses.AbstractClasspathType.name)
    .withFlag(ManifestationFlag.ABSTRACT)
    .withMethod(new Reference.Source[0], new Reference.Flag[0], "foo", Type.VOID_TYPE)
    .withMethod(new Reference.Source[0], [ManifestationFlag.ABSTRACT] as Reference.Flag[], "abstract", Type.INT_TYPE)
    .build()

  @Shared
  def helperClass = new Reference.Builder(HelperReferenceWrapperTest.name + '$Helper')
    .withSuperName(baseHelperClass.className)
    .withInterface(HelperReferenceWrapperTestClasses.Interface2.name)
    .withMethod(new Reference.Source[0], new Reference.Flag[0], "bar", Type.VOID_TYPE)
    .withField(new Reference.Source[0], new Reference.Flag[0], "field", Type.getType("Ljava/lang/Object;"), false)
    .withField(new Reference.Source[0], new Reference.Flag[0], "declaredField", Type.getType("Ljava/lang/Object;"), true)
    .withField(new Reference.Source[0], [Reference.Flag.VisibilityFlag.PRIVATE] as Reference.Flag[], "privateFieldsAreSkipped", Type.getType("Ljava/lang/Object;"), true)
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

      with(helper.methods.collect(toList())) {
        it.size() == 1
        with(it[0]) {
          !it.abstract
          it.name == "bar"
          it.descriptor == "()V"
        }
      }

      with(helper.fields.collect(toList())) {
        it.size() == 1
        with(it[0]) {
          it.name == "declaredField"
          it.descriptor == "Ljava/lang/Object;"
        }
      }

      helper.hasSuperTypes()
      with(helper.superTypes.collect(toList())) {
        it.size() == 2
        with(it[0]) { baseHelper ->
          baseHelper.abstract

          with(baseHelper.methods.collect(toList())) {
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
          with(baseHelper.superTypes.collect(toList())) {
            it.size() == 1
            with(it[0]) { abstractClasspathType ->
              abstractClasspathType.abstract

              abstractClasspathType.getMethods().collect(toList()).isEmpty()

              with(abstractClasspathType.fields.collect(toList())) {
                it.size() == 1
                with(it[0]) {
                  it.name == "field"
                  it.descriptor == "Ljava/lang/Object;"
                }
              }

              abstractClasspathType.hasSuperTypes()
              with(abstractClasspathType.superTypes.collect(toList())) {
                it.size() == 2
                with(it[0]) { object ->
                  !object.hasSuperTypes()
                }
                with(it[1]) { interface1 ->
                  interface1.abstract

                  with(interface1.methods.collect(toList())) {
                    it.size() == 1
                    with(it[0]) {
                      it.abstract
                      it.name == "foo"
                      it.descriptor == "()V"
                    }
                  }

                  !interface1.hasSuperTypes()
                  interface1.getSuperTypes().collect(toList()).isEmpty()
                }
              }
            }
          }
        }
        with(it[1]) { interface2 ->
          interface2.abstract

          with(interface2.methods.collect(toList())) {
            it.size() == 1
            with(it[0]) {
              it.abstract
              it.name == "bar"
              it.descriptor == "()V"
            }
          }

          !interface2.hasSuperTypes()
          interface2.getSuperTypes().collect(toList()).isEmpty()
        }
      }
    }
  }
}
