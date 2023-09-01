plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("javax.servlet")
    module.set("servlet-api")
    versions.set("[2.2, 3.0)")
    assertInverse.set(true)
  }

  fail {
    group.set("javax.servlet")
    module.set("javax.servlet-api")
    versions.set("[3.0,)")
  }
}

dependencies {
  implementation(project(":instrumentation:servlet:servlet-common:javaagent"))
  implementation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("javax.servlet:servlet-api:2.2")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))

  testLibrary("org.eclipse.jetty:jetty-server:7.0.0.v20091005")
  testLibrary("org.eclipse.jetty:jetty-servlet:7.0.0.v20091005")

  latestDepTestLibrary("org.eclipse.jetty:jetty-server:7.+") // see servlet-3.0 module
  latestDepTestLibrary("org.eclipse.jetty:jetty-servlet:7.+") // see servlet-3.0 module
}

tasks {
  val testStableSemconv by registering(Test::class) {
    filter {
      includeTestsMatching("JettyServlet2Test")
    }
    include("**/JettyServlet2Test.*")

    jvmArgs("-Dotel.semconv-stability.opt-in=http")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
