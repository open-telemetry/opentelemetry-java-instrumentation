plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.glassfish")
    module.set("jakarta.faces")
    versions.set("[2.3.9,3)")
    extraDependency("javax.el:el-api:2.2")
  }
  pass {
    group.set("org.glassfish")
    module.set("javax.faces")
    versions.set("[2.0.7,3)")
    extraDependency("javax.el:el-api:2.2")
  }
  pass {
    group.set("com.sun.faces")
    module.set("jsf-impl")
    versions.set("[2.1,2.2)")
    extraDependency("javax.faces:jsf-api:2.1")
    extraDependency("javax.el:el-api:1.0")
  }
  pass {
    group.set("com.sun.faces")
    module.set("jsf-impl")
    versions.set("[2.0,2.1)")
    extraDependency("javax.faces:jsf-api:2.0")
    extraDependency("javax.el:el-api:1.0")
  }
  pass {
    group.set("javax.faces")
    module.set("jsf-impl")
    versions.set("[1.2,2)")
    extraDependency("javax.faces:jsf-api:1.2")
    extraDependency("javax.el:el-api:1.0")
  }
  fail {
    group.set("org.glassfish")
    module.set("jakarta.faces")
    versions.set("[3.0,)")
    extraDependency("javax.el:el-api:2.2")
  }
}

dependencies {
  compileOnly("javax.faces:jsf-api:1.2")

  implementation(project(":instrumentation:jsf:jsf-javax-common:javaagent"))

  testImplementation(project(":instrumentation:jsf:jsf-javax-common:testing"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
}

val latestDepTest = findProperty("testLatestDeps") as Boolean
testing {
  suites {
    val mojarra12Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:jsf:jsf-javax-common:testing"))
        implementation("javax.faces:jsf-api:1.2")
        implementation("com.sun.facelets:jsf-facelets:1.1.14")

        if (latestDepTest) {
          implementation("javax.faces:jsf-impl:1.+")
        } else {
          implementation("javax.faces:jsf-impl:1.2_04")
        }
      }
    }

    val mojarra2Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:jsf:jsf-javax-common:testing"))

        if (latestDepTest) {
          implementation("org.glassfish:jakarta.faces:2.+")
        } else {
          implementation("org.glassfish:javax.faces:2.2.0")
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
}
