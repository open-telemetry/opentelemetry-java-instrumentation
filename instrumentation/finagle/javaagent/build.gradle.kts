plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  pass {
    group.set("com.twitter")
    module.set("finagle-core_2.12")
    versions.set("[23.11.0,]")
  }

  pass {
    group.set("com.twitter")
    module.set("finagle-core_2.13")
    versions.set("[23.11.0,]")
  }

  pass {
    group.set("com.twitter")
    module.set("finagle-http2_2.12")
    versions.set("[23.11.0,]")
  }

  pass {
    group.set("com.twitter")
    module.set("finagle-http2_2.13")
    versions.set("[23.11.0,]")
  }

  pass {
    group.set("com.twitter")
    module.set("finagle-netty4_2.12")
    versions.set("[23.11.0,]")
  }

  pass {
    group.set("com.twitter")
    module.set("finagle-netty4_2.13")
    versions.set("[23.11.0,]")
  }

  pass {
    group.set("com.twitter")
    module.set("finagle-netty4-http_2.12")
    versions.set("[23.11.0,]")
  }

  pass {
    group.set("com.twitter")
    module.set("finagle-netty4-http_2.13")
    versions.set("[23.11.0,]")
  }
}

val finagleVersion = "23.11.0"
val scalaVersion = "2.13.10"

val scalaMinor = Regex("""^([0-9]+\.[0-9]+)\.?.*$""").find(scalaVersion)!!.run {
  val (minorVersion) = this.destructured
  minorVersion
}

val scalified = fun(pack: String): String {
  return "${pack}_$scalaMinor"
}

dependencies {
  // needed for direct access to shaded classes
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))

  // should wire netty contexts
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))

  implementation(project(":instrumentation:netty:netty-4.1:javaagent"))
  implementation(project(":instrumentation:netty:netty-4.1:library"))
  implementation(project(":instrumentation:netty:netty-4-common:library"))

  compileOnly("${scalified("com.twitter:finagle-core")}:$finagleVersion")
  compileOnly("${scalified("com.twitter:finagle-http")}:$finagleVersion")

  testImplementation("${scalified("com.twitter:finagle-core")}:$finagleVersion")
  testImplementation("${scalified("com.twitter:finagle-http")}:$finagleVersion")
}
