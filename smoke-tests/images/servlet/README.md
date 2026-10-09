# Smoke Test Environment Matrix

This project builds docker images containing a simple test web application deployed to various
application servers or servlet containers. For each server several relevant versions are chosen.
In addition we build separate images for several support major java versions.
This way we can test our agent with many different combinations of runtime environment,
its version and running on different JVM versions from different vendors.

Linux WildFly images use tar.gz distributions by default. The WildFly 41.0.1.Final target selects
ZIP with the `archiveFormat` build argument. Windows WildFly images use ZIP distributions.
