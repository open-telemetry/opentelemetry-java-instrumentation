# Library Instrumentation for Netty version 4.1 and higher

Provides OpenTelemetry instrumentation for [Netty](https://netty.io/), enabling HTTP client and
server spans and metrics.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-netty-4.1).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-netty-4.1</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-netty-4.1:OPENTELEMETRY_VERSION")
```

### Usage

#### HTTP Client

```java
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.netty.v4_1.NettyClientTelemetry;

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

NettyClientTelemetry clientTelemetry = NettyClientTelemetry.create(openTelemetry);

EventLoopGroup eventLoopGroup = ...; // Use appropriate EventLoopGroup for your platform
Class<? extends Channel> channelClass = ...; // Use appropriate Channel class for your platform

Bootstrap bootstrap = new Bootstrap();
bootstrap.group(eventLoopGroup)
    .channel(channelClass)
    .handler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) {
            ch.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(clientTelemetry.createCombinedHandler())
                .addLast(new YourClientHandler()); // Your application handler
        }
    });

Channel channel = bootstrap.connect("localhost", 8080).sync().channel();
NettyClientTelemetry.setChannelContext(channel, Context.current());
```

#### HTTP Server

```java
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.netty.v4_1.NettyServerTelemetry;

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

NettyServerTelemetry serverTelemetry = NettyServerTelemetry.create(openTelemetry);

EventLoopGroup bossGroup = ...; // Use appropriate EventLoopGroup for your platform
EventLoopGroup workerGroup = ...; // Use appropriate EventLoopGroup for your platform
Class<? extends ServerChannel> serverChannelClass = ...; // Use appropriate ServerChannel class for your platform

ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(bossGroup, workerGroup)
    .channel(serverChannelClass)
    .childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) {
            ch.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(serverTelemetry.createCombinedHandler())
                .addLast(new YourServerHandler()); // Your application handler
        }
    });

bootstrap.bind(8080).sync();
```
