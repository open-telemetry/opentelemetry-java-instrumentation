import com.google.protobuf.gradle.*

plugins {
  id("otel.javaagent-instrumentation")
  id("com.google.protobuf")
}

muzzle {
  pass {
    group.set("com.linecorp.armeria")
    module.set("armeria-grpc")
    versions.set("[1.14.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.linecorp.armeria:armeria-grpc:1.14.0")
  implementation(project(":instrumentation:grpc-1.6:library"))

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:grpc-1.6:javaagent"))

  testLibrary("com.linecorp.armeria:armeria-junit5:1.14.0")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean
protobuf {
  protoc {
    val protocVersion = if (latestDepTest) "3.25.5" else "3.19.2"
    artifact = "com.google.protobuf:protoc:$protocVersion"
  }
  plugins {
    id("grpc") {
      val grpcVersion = if (latestDepTest) "1.43.2" else "1.68.1"
      artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
    }
  }
  generateProtoTasks {
    all().configureEach {
      plugins {
        id("grpc")
      }
    }
  }
}

afterEvaluate {
  // Classpath when compiling protos, we add dependency management directly
  // since it doesn't follow Gradle conventions of naming / properties.
  dependencies {
    add("compileProtoPath", platform(project(":dependencyManagement")))
    add("testCompileProtoPath", platform(project(":dependencyManagement")))
  }
}
