/*
 * Instrumentation for Hibernate between 3.5 and 4.
 * Has the same logic as the Hibernate 4+ instrumentation, but is copied rather than sharing a codebase. This is because
 * the root interface for Session/StatelessSession - SharedSessionContract - isn't present before version 4. So the
 * instrumentation isn't able to reference it.
 */

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.hibernate")
    module.set("hibernate-core")
    versions.set("[3.3.0.GA,4.0.0.Final)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.hibernate:hibernate-core:3.3.0.GA")

  implementation(project(":instrumentation:hibernate:hibernate-common:javaagent"))

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  // Added to ensure cross compatibility:
  testInstrumentation(project(":instrumentation:hibernate:hibernate-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:hibernate:hibernate-procedure-call-4.3:javaagent"))

  testLibrary("org.hibernate:hibernate-core:3.3.0.SP1")
  testImplementation("org.hibernate:hibernate-annotations:3.4.0.GA")
  testImplementation("javassist:javassist:+")
  testImplementation("com.h2database:h2:1.4.197")
  testImplementation("javax.xml.bind:jaxb-api:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-core:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-impl:2.2.11")
  testImplementation("javax.activation:activation:1.1.1")

  latestDepTestLibrary("org.hibernate:hibernate-core:3.+")
}

if (findProperty("testLatestDeps") as Boolean) {
  configurations {
    // Needed for test, but for latestDepTest this would otherwise bundle a second incompatible version of hibernate-core.
    testImplementation {
      exclude("org.hibernate", "hibernate-annotations")
    }
  }
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.hibernate.experimental-span-attributes=true")
}
