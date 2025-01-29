plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.restlet")
    module.set("org.restlet")
    versions.set("[1.1.0, 1.2-M1)")
    extraDependency("com.noelios.restlet:com.noelios.restlet")
    // missing dependencies
    skip("2.5.0", "2.5.1")
    assertInverse.set(true)
  }
}

repositories {
  mavenCentral()
  maven("https://maven.restlet.talend.com/")
  mavenLocal()
}

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  implementation(project(":instrumentation:restlet:restlet-1.1:library"))

  library("org.restlet:org.restlet:1.1.5")
  library("com.noelios.restlet:com.noelios.restlet:1.1.5")

  testImplementation(project(":instrumentation:restlet:restlet-1.1:testing"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))

  latestDepTestLibrary("org.restlet:org.restlet:1.+") // see restlet-2.0 module
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
