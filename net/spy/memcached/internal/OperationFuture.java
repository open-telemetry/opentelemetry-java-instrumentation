/**
 * Copyright (C) 2006-2009 Dustin Sallings
 * Copyright (C) 2009-2013 Couchbase, Inc.
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

package net.spy.memcached.internal;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.ops.Operation;
import net.spy.memcached.ops.OperationState;
import net.spy.memcached.ops.OperationStatus;
import net.spy.memcached.ops.StatusCode;

/**
 * Managed future for operations.
 *
 * <p>From an OperationFuture, application code can determine if the status of a
 * given Operation in an asynchronous manner.
 *
 * <p>If for example we needed to update the keys "user:<userid>:name",
 * "user:<userid>:friendlist" because later in the method we were going to
 * verify the change occurred as expected interacting with the user, we can
 * fire multiple IO operations simultaneously with this concept.
 *
 * @param <T> Type of object returned from this future.
 */
public class OperationFuture<T>
  extends AbstractListenableFuture<T, OperationCompletionListener>
  implements Future<T> {

  private final CountDownLatch latch;
  private final AtomicReference<T> objRef;
  protected OperationStatus status;
  private final long timeout;
  private Operation op;
  private final String key;
  private Long cas;

  /**
   * Create an OperationFuture for a given async operation.
   *
   * This is intended for internal use only.
   *
   * @param k the key for the operation
   * @param l the latch to be used counting down the OperationFuture
   * @param opTimeout the timeout within which the operation needs to be done
   */
  public OperationFuture(String k, CountDownLatch l, long opTimeout,
    ExecutorService service) {
    this(k, l, new AtomicReference<T>(null), opTimeout, service);
  }

  /**
   * Create an OperationFuture for a given async operation.
   *
   * This is intended for internal use only.
   *
   * @param k the key for the operation
   * @param l the latch to be used counting down the OperationFuture
   * @param oref an AtomicReference associated with the operation
   * @param opTimeout the timeout within which the operation needs to be done
   */
  public OperationFuture(String k, CountDownLatch l, AtomicReference<T> oref,
      long opTimeout, ExecutorService service) {
    super(service);

    latch = l;
    objRef = oref;
    status = null;
    timeout = opTimeout;
    key = k;
    cas = null;
  }

  /**
   * Cancel this operation, if possible.
   *
   * @param ign not used
   * @deprecated
   * @return true if the operation has not yet been written to the network
   */
  public boolean cancel(boolean ign) {
    assert op != null : "No operation";
    op.cancel();
    notifyListeners();
    return op.getState() == OperationState.WRITE_QUEUED;
  }

  /**
   * Cancel this operation, if possible.
   *
   * @return true if the operation has not yet been written to the network
   */
  public boolean cancel() {
    assert op != null : "No operation";
    op.cancel();
    notifyListeners();
    return op.getState() == OperationState.WRITE_QUEUED;
  }

  /**
   * Get the results of the given operation.
   *
   * As with the Future interface, this call will block until the results of
   * the future operation has been received.
   *
   * @return the operation results of this OperationFuture
   * @throws InterruptedException
   * @throws ExecutionException
   */
  public T get() throws InterruptedException, ExecutionException {
    try {
      return get(timeout, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new RuntimeException("Timed out waiting for operation", e);
    }
  }

  /**
   * Get the results of the given operation.
   *
   * As with the Future interface, this call will block until the results of
   * the future operation has been received.
   *
   * @param duration amount of time to wait
   * @param units unit of time to wait
   * @return the operation results of this OperationFuture
   * @throws InterruptedException
   * @throws TimeoutException
   * @throws ExecutionException
   */
  public T get(long duration, TimeUnit units) throws InterruptedException,
      TimeoutException, ExecutionException {
    if (!latch.await(duration, units)) {
      // whenever timeout occurs, continuous timeout counter will increase by 1.
      MemcachedConnection.opTimedOut(op);
      if (op != null) { // op can be null on a flush
        op.timeOut();
      }
      throw new CheckedOperationTimeoutException(
          "Timed out waiting for operation", op);
    } else {
      // continuous timeout counter will be reset
      MemcachedConnection.opSucceeded(op);
    }
    if (op != null && op.hasErrored()) {
      throw new ExecutionException(op.getException());
    }
    if (isCancelled()) {
      throw new ExecutionException(new CancellationException("Cancelled"));
    }
    if (op != null && op.isTimedOut()) {
      throw new ExecutionException(new CheckedOperationTimeoutException(
          "Operation timed out.", op));
    }

    /* TODO: re-add assertion that op.getState() == OperationState.COMPLETE */

    return objRef.get();
  }

  /**
   * Get the key for this operation.
   *
   * @return the key for this operation
   */
  public String getKey() {
    return key;
  }

 /**
   * Set the key for this operation.
   *
   * @param inCas the CAS value
   */
  public void setCas(long inCas) {
    this.cas = inCas;
  }

 /**
   * Get the CAS for this operation.
   *
   * @throws UnsupportedOperationException If this is for an ASCII protocol
   * configured client.
   * @return the CAS for this operation or null if unsuccessful.
   *
   */
  public Long getCas() {
    if (cas == null) {
      try {
        get();
      } catch (InterruptedException e) {
        status = new OperationStatus(false, "Interrupted", StatusCode.INTERRUPTED);
        Thread.currentThread().isInterrupted();
      } catch (ExecutionException e) {
        getLogger().warn("Error getting cas of operation", e);
      }
    }
    if (cas == null && status.isSuccess()) {
      throw new UnsupportedOperationException("This operation doesn't return"
          + "a cas value.");
    }
    return cas;
  }
  /**
   * Get the current status of this operation.
   *
   * Note that the operation status may change as the operation is tried and
   * potentially retried against the servers specified by the NodeLocator.
   *
   * @return OperationStatus
   */
  public OperationStatus getStatus() {
    if (status == null) {
      try {
        get();
      } catch (InterruptedException e) {
        status = new OperationStatus(false, "Interrupted", StatusCode.INTERRUPTED);
        Thread.currentThread().isInterrupted();
      } catch (ExecutionException e) {
        getLogger().warn("Error getting status of operation", e);
      }
    }
    return status;
  }

  /**
   * Set the Operation associated with this OperationFuture.
   *
   * This is intended for internal use only.
   *
   * @param o the Operation object itself
   * @param s the OperationStatus associated with this operation
   */
  public void set(T o, OperationStatus s) {
    objRef.set(o);
    status = s;
  }

  /**
   *  Set the Operation associated with this OperationFuture.
   *
   * This is intended for internal use only.
   *
   * @param to the Operation to set this OperationFuture to be tracking
   */
  public void setOperation(Operation to) {
    op = to;
  }

  /**
   * Whether or not the Operation associated with this OperationFuture has been
   * canceled.
   *
   * One scenario in which this can occur is if the connection is lost and the
   * Operation has been sent over the network.  In this case, the operation may
   * or may not have reached the server before the connection was dropped.
   *
   * @return true if the Operation has been canceled
   */
  public boolean isCancelled() {
    assert op != null : "No operation";
    return op.isCancelled();
  }

  /**
   * Whether or not the Operation is done and result can be retrieved with
   * get().
   *
   * The most common way to wait for this OperationFuture is to use the get()
   * method which will block.  This method allows one to check if it's complete
   * without blocking.
   *
   * @return true if the Operation is done
   */
  public boolean isDone() {
    assert op != null : "No operation";
    return latch.getCount() == 0 || op.isCancelled()
        || op.getState() == OperationState.COMPLETE;
  }

  @Override
  public OperationFuture<T> addListener(OperationCompletionListener listener) {
    super.addToListeners((GenericCompletionListener) listener);
    return this;
  }

  @Override
  public OperationFuture<T> removeListener(
    OperationCompletionListener listener) {
    super.removeFromListeners((GenericCompletionListener) listener);
    return this;
  }

  /**
   * Signals that this future is complete.
   */
  public void signalComplete() {
    notifyListeners();
  }

}
