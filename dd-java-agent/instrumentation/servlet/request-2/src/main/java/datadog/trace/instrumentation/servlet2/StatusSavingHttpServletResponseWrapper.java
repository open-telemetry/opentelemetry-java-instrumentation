package datadog.trace.instrumentation.servlet2;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class StatusSavingHttpServletResponseWrapper extends HttpServletResponseWrapper {
  public int status = 200;

  public StatusSavingHttpServletResponseWrapper(final HttpServletResponse response) {
    super(response);
  }

  @Override
  public void sendError(final int status) throws IOException {
    this.status = status;
    super.sendError(status);
  }

  @Override
  public void sendError(final int status, final String message) throws IOException {
    this.status = status;
    super.sendError(status, message);
  }

  @Override
  public void sendRedirect(final String location) throws IOException {
    status = 302;
    super.sendRedirect(location);
  }

  @Override
  public void setStatus(final int status) {
    this.status = status;
    super.setStatus(status);
  }

  @Override
  public void setStatus(final int status, final String message) {
    this.status = status;
    super.setStatus(status, message);
  }
}
