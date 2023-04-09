package io.opentelemetry.instrumentation.netty.v4.common.internal.client;

import io.netty.channel.AbstractChannel;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;

/**
 * Specialization for extracting any Netty-private implementations of known exceptions.
 *
 * <p>Most notably, {@link AbstractChannel.AnnotatedConnectException}, {@link
 * AbstractChannel.AnnotatedNoRouteToHostException}, and {@link
 * AbstractChannel.AnnotatedSocketException}. All are private and so present an interface which --
 * when extracted literally -- exposes the non-public names which aren't useful during a) tests, or
 * b) runtime, and ultimately betray the nature of their otherwise public, and implemented,
 * counterparts.
 */
class NettyErrorCauseExtractor implements ErrorCauseExtractor {
  static final NettyErrorCauseExtractor INSTANCE = new NettyErrorCauseExtractor();

  private NettyErrorCauseExtractor() {}

  @Override
  public Throwable extract(Throwable error) {
    Class<?> clazz = error.getClass();
    if (ConnectException.class.isAssignableFrom(clazz) && !ConnectException.class.equals(clazz)) {
      return new ConnectException(error.getMessage());
    } else if (NoRouteToHostException.class.isAssignableFrom(clazz)
        && !NoRouteToHostException.class.equals(clazz)) {
      return new NoRouteToHostException(error.getMessage());
    } else if (SocketException.class.isAssignableFrom(clazz)
        && !SocketException.class.equals(clazz)) {
      return new SocketException(error.getMessage());
    }
    return ErrorCauseExtractor.getDefault().extract(error);
  }
}
