plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.myfaces.core")
    module.set("myfaces-impl")
    versions.set("[1.2,3)")
    extraDependency("jakarta.el:jakarta.el-api:3.0.3")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("org.apache.myfaces.core:myfaces-api:1.2.12")
  compileOnly("javax.el:el-api:1.0")

  implementation(project(":instrumentation:jsf:jsf-javax-common:javaagent"))

  testImplementation(project(":instrumentation:jsf:jsf-javax-common:testing"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
}

val latestDepTest = findProperty("testLatestDeps") as Boolean
testing {
  suites {
    val myfaces12Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:jsf:jsf-javax-common:testing"))
        implementation("com.sun.facelets:jsf-facelets:1.1.14")

        if (latestDepTest) {
          implementation("org.apache.myfaces.core:myfaces-impl:1.2.+")
        } else {
          implementation("org.apache.myfaces.core:myfaces-impl:1.2.2")
        }
      }
    }

    val myfaces2Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:jsf:jsf-javax-common:testing"))
        implementation("javax.xml.bind:jaxb-api:2.2.11")
        implementation("com.sun.xml.bind:jaxb-impl:2.2.11")

        if (latestDepTest) {
          implementation("org.apache.myfaces.core:myfaces-impl:2.+")
        } else {
          implementation("org.apache.myfaces.core:myfaces-impl:2.2.0")
        }
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
  jvmArgs("-Dotel.instrumentation.common.experimental.view-telemetry.enabled=true")
}
