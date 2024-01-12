val stableVersion = "2.0.0"
val alphaVersion = "2.0.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
