val stableVersion = "2.3.0"
val alphaVersion = "2.3.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
