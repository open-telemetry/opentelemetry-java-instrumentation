/**
 * Copyright (C) 2006-2009 Dustin Sallings
 * Copyright (C) 2009-2012 Couchbase, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */

package net.spy.memcached.ops;

import java.io.IOException;

/**
 * Exceptions thrown when protocol errors occur.
 */
public class OperationException extends IOException {

  private static final long serialVersionUID = 1524499960923239786L;

  private final OperationErrorType type;

  /**
   * General exception (no message).
   */
  public OperationException() {
    super();
    type = OperationErrorType.GENERAL;
  }

  /**
   * Exception with a message.
   *
   * @param eType the type of error that occurred
   * @param msg the error message
   */
  public OperationException(OperationErrorType eType, String msg) {
    super(msg);
    type = eType;
  }

  /**
   * Get the type of error.
   */
  public OperationErrorType getType() {
    return type;
  }

  @Override
  public String toString() {
    String rv = null;
    if (type == OperationErrorType.GENERAL) {
      rv = "OperationException: " + type;
    } else {
      rv = "OperationException: " + type + ": " + getMessage();
    }
    return rv;
  }
}
