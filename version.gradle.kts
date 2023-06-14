val stableVersion = "1.27.0"
val alphaVersion = "1.27.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
