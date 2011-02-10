/**
 * Copyright 2010 Voxeo Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.voxeo.moho.media;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.media.mscontrol.mediagroup.MediaGroup;
import javax.media.mscontrol.mediagroup.signals.SignalDetector;

import com.voxeo.moho.event.InputCompleteEvent;
import com.voxeo.moho.util.SettableResultFuture;

public class InputImpl implements Input {

  protected MediaGroup _group;

  protected SettableResultFuture<InputCompleteEvent> _future = new SettableResultFuture<InputCompleteEvent>();

  protected InputImpl(final MediaGroup group) {
    _group = group;
  }

  protected void done(final InputCompleteEvent event) {
    _future.setResult(event);
  }

  @Override
  public void stop() {
    if (!_future.isDone()) {
      _group.triggerAction(SignalDetector.STOP);
    }
  }

  @Override
  public boolean cancel(final boolean mayInterruptIfRunning) {
    return _future.cancel(mayInterruptIfRunning);
  }

  @Override
  public InputCompleteEvent get() throws InterruptedException, ExecutionException {
    return _future.get();
  }

  @Override
  public InputCompleteEvent get(final long timeout, final TimeUnit unit) throws InterruptedException,
      ExecutionException, TimeoutException {
    return _future.get(timeout, unit);
  }

  @Override
  public boolean isCancelled() {
    return _future.isCancelled();
  }

  @Override
  public boolean isDone() {
    return _future.isDone();
  }

  public synchronized boolean isPending() {
    return !_future.isDone();
  }
}
