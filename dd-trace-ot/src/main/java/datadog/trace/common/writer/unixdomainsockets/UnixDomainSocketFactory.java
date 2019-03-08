package datadog.trace.common.writer.unixdomainsockets;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.net.SocketFactory;
import jnr.unixsocket.UnixSocketChannel;

/**
 * Impersonate TCP-style SocketFactory over UNIX domain sockets.
 *
 * <p>Copied from <a
 * href="https://github.com/square/okhttp/blob/master/samples/unixdomainsockets/src/main/java/okhttp3/unixdomainsockets/UnixDomainSocketFactory.java">okHttp
 * examples</a>.
 */
public final class UnixDomainSocketFactory extends SocketFactory {
  private final File path;

  public UnixDomainSocketFactory(final File path) {
    this.path = path;
  }

  @Override
  public Socket createSocket() throws IOException {
    final UnixSocketChannel channel = UnixSocketChannel.open();
    return new TunnelingUnixSocket(path, channel);
  }

  @Override
  public Socket createSocket(final String host, final int port) throws IOException {
    final Socket result = createSocket();
    result.connect(new InetSocketAddress(host, port));
    return result;
  }

  @Override
  public Socket createSocket(
      final String host, final int port, final InetAddress localHost, final int localPort)
      throws IOException {
    return createSocket(host, port);
  }

  @Override
  public Socket createSocket(final InetAddress host, final int port) throws IOException {
    final Socket result = createSocket();
    result.connect(new InetSocketAddress(host, port));
    return result;
  }

  @Override
  public Socket createSocket(
      final InetAddress host, final int port, final InetAddress localAddress, final int localPort)
      throws IOException {
    return createSocket(host, port);
  }
}
