package datadog.trace.common.writer.unixdomainsockets;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import jnr.unixsocket.UnixSocket;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;

/**
 * Subtype UNIX socket for a higher-fidelity impersonation of TCP sockets. This is named "tunneling"
 * because it assumes the ultimate destination has a hostname and port.
 *
 * <p>Copied from <a
 * href="https://github.com/square/okhttp/blob/master/samples/unixdomainsockets/src/main/java/okhttp3/unixdomainsockets/UnixDomainServerSocketFactory.java">okHttp
 * examples</a>.
 */
final class TunnelingUnixSocket extends UnixSocket {
  private final File path;
  private InetSocketAddress inetSocketAddress;

  TunnelingUnixSocket(final File path, final UnixSocketChannel channel) {
    super(channel);
    this.path = path;
  }

  TunnelingUnixSocket(
      final File path, final UnixSocketChannel channel, final InetSocketAddress address) {
    this(path, channel);
    inetSocketAddress = address;
  }

  @Override
  public void connect(final SocketAddress endpoint) throws IOException {
    inetSocketAddress = (InetSocketAddress) endpoint;
    super.connect(new UnixSocketAddress(path), 0);
  }

  @Override
  public void connect(final SocketAddress endpoint, final int timeout) throws IOException {
    inetSocketAddress = (InetSocketAddress) endpoint;
    super.connect(new UnixSocketAddress(path), timeout);
  }

  @Override
  public InetAddress getInetAddress() {
    return inetSocketAddress.getAddress();
  }
}
