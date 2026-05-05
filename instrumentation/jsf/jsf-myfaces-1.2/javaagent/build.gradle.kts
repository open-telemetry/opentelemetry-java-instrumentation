plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.myfaces.core")
    module.set("myfaces-impl")
    versions.set("[1.2,3)")
    assertInverse.set(true)
    extraDependency("jakarta.el:jakarta.el-api:3.0.3")
  }
}

dependencies {
  compileOnly("org.apache.myfaces.core:myfaces-api:1.2.12")
  compileOnly("javax.el:el-api:1.0")

  implementation(project(":instrumentation:jsf:jsf-common-javax:javaagent"))

  testImplementation(project(":instrumentation:jsf:jsf-common-javax:testing"))

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jsf:jsf-myfaces-3.0:javaagent"))
}

testing {
  suites {
    val myfaces12Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:jsf:jsf-common-javax:testing"))
        implementation("com.sun.facelets:jsf-facelets:1.1.14")

        val version = baseVersion("1.2.2").orLatest("1.2.+")
        implementation("org.apache.myfaces.core:myfaces-impl:$version")
      }
    }

    val myfaces2Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:jsf:jsf-common-javax:testing"))
        implementation("javax.xml.bind:jaxb-api:2.2.11")
        implementation("com.sun.xml.bind:jaxb-impl:2.2.11")

        val version = baseVersion("2.2.0").orLatest("2.+")
        implementation("org.apache.myfaces.core:myfaces-impl:$version")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  systemProperty("collectMetadata", otelProps.collectMetadata)
  systemProperty("metadataConfig", "otel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
