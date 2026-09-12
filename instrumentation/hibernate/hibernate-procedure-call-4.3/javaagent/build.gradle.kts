plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.hibernate")
    module.set("hibernate-core")
    versions.set("[4.3.0.Final,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.hibernate:hibernate-core:4.3.0.Final")

  implementation(project(":instrumentation:hibernate:hibernate-common-3.3:javaagent"))

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  testImplementation(project(":instrumentation:hibernate:testing"))
  // Added to ensure cross compatibility:
  testInstrumentation(project(":instrumentation:hibernate:hibernate-3.3:javaagent"))
  testInstrumentation(project(":instrumentation:hibernate:hibernate-4.0:javaagent"))

  testLibrary("org.hibernate:hibernate-entitymanager:4.3.0.Final")

  testImplementation("org.hsqldb:hsqldb:2.0.0")
  testImplementation("javax.xml.bind:jaxb-api:2.3.1")
  testImplementation("org.glassfish.jaxb:jaxb-runtime:2.3.3")

  latestDepTestLibrary("org.hibernate:hibernate-core:5.+") // see hibernate-6.0 module
  latestDepTestLibrary("org.hibernate:hibernate-entitymanager:5.+") // see hibernate-6.0 module
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.hibernate.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.hibernate.experimental-span-attributes=true")
  }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    jvmArgs("-Dotel.instrumentation.hibernate.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.common.v3-preview=true,otel.instrumentation.hibernate.enabled=true",
    )
  }

  val testV3PreviewDisabled = register<Test>("testV3PreviewDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("ProcedureCallTest.v3PreviewDisablesHibernateByDefault")
    }

    jvmArgs("-DtestV3PreviewDisabled=true")
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    jvmArgs("-Dotel.instrumentation.jdbc.enabled=false")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv, testExperimental, testV3Preview, testV3PreviewDisabled)
  }
}
