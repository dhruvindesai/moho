/**
 * Copyright 2010 Voxeo Corporation Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.voxeo.moho;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;

import javax.media.mscontrol.MediaObject;
import javax.media.mscontrol.MediaSession;
import javax.media.mscontrol.MsControlException;
import javax.media.mscontrol.MsControlFactory;
import javax.media.mscontrol.Parameters;
import javax.media.mscontrol.join.Joinable;
import javax.media.mscontrol.join.JoinableStream;
import javax.media.mscontrol.join.Joinable.Direction;
import javax.media.mscontrol.join.JoinableStream.StreamType;
import javax.media.mscontrol.mixer.MediaMixer;
import javax.media.mscontrol.mixer.MixerAdapter;
import javax.media.mscontrol.spi.Driver;
import javax.media.mscontrol.spi.DriverManager;

import org.apache.log4j.Logger;

import com.voxeo.moho.event.DispatchableEventSource;
import com.voxeo.moho.event.EventSource;
import com.voxeo.moho.event.JoinCompleteEvent;
import com.voxeo.moho.event.MediaResourceDisconnectEvent;
import com.voxeo.moho.event.Observer;
import com.voxeo.moho.event.JoinCompleteEvent.Cause;
import com.voxeo.utils.Event;
import com.voxeo.utils.EventListener;

public class MixerImpl extends DispatchableEventSource implements Mixer, ParticipantContainer {

  private static final Logger LOG = Logger.getLogger(MixerImpl.class);

  protected MixerEndpoint _address;

  protected MediaService _service;

  protected MediaSession _media;

  protected MediaMixer _mixer;

  protected MixerAdapter _adapter;

  protected MyMixerAdapter _adapterParticipant;

  protected boolean _clampDtmf;

  protected JoineeData _joinees = new JoineeData();

  protected MixerImpl(final ExecutionContext context, final MixerEndpoint address, final Map<Object, Object> params,
      Parameters parameters) {
    super(context);
    try {
      MsControlFactory mf = null;
      if (params == null || params.size() == 0) {
        mf = context.getMSFactory();
      }
      else {
        final Driver driver = DriverManager.getDrivers().next();
        final Properties props = new Properties();
        for (final Map.Entry<Object, Object> entry : params.entrySet()) {
          final String key = String.valueOf(entry.getKey());
          final String value = entry.getValue() == null ? "" : entry.getValue().toString();
          props.setProperty(key, value);
        }
        if (props.getProperty(MsControlFactory.MEDIA_SERVER_URI) == null && address != null) {
          props.setProperty(MsControlFactory.MEDIA_SERVER_URI, address.getURI().toString());
        }
        mf = driver.getFactory(props);
      }
      _media = mf.createMediaSession();
      _mixer = _media.createMediaMixer(MediaMixer.AUDIO, parameters);
      _address = address;

      _adapter = _mixer.createMixerAdapter(MixerAdapter.DTMF_CLAMP);
      _adapterParticipant = new MyMixerAdapter();

      if ((address.getProperty("playTones") != null && !Boolean.valueOf(address.getProperty("playTones")))
          || (params != null && !Boolean.valueOf((String) params.get("playTones")))) {
        _clampDtmf = true;
      }
    }
    catch (final Exception e) {
      throw new MediaException(e);
    }
  }

  @Override
  public int hashCode() {
    return _mixer.hashCode();
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof MixerImpl)) {
      return false;
    }
    if (this == o) {
      return true;
    }
    return _mixer.equals(((MixerImpl) o).getMediaObject());
  }

  @Override
  public String toString() {
    return new StringBuilder().append(MixerImpl.class.getSimpleName()).append("[").append(_mixer).append("]")
        .toString();
  }

  @Override
  public synchronized MediaService getMediaService() throws MediaException, IllegalStateException {
    checkState();
    if (_service == null) {
      try {
        _service = _context.getMediaServiceFactory().create(this, _media, null);
        _service.getMediaGroup().join(Direction.DUPLEX, _mixer);
        return _service;
      }
      catch (final Exception e) {
        throw new MediaException(e);
      }
    }
    return _service;
  }

  @Override
  public synchronized void disconnect() {
    try {
      _mixer.release();
    }
    catch (final Exception e) {
      LOG.warn("Exception when release mixer", e);
    }
    try {
      _media.release();
    }
    catch (final Exception e) {
      LOG.warn("Exception when release mediaSession", e);
    }
    _media = null;

    this.dispatch(new MediaResourceDisconnectEvent(this));
  }

  @Override
  public Endpoint getAddress() {
    return _address;
  }

  @Override
  public Participant[] getParticipants() {
    return _joinees.getJoinees();
  }

  @Override
  public Participant[] getParticipants(final Direction direction) {
    return _joinees.getJoinees(direction);
  }

  @Override
  public void addParticipant(final Participant p, final JoinType type, final Direction direction) {
    _joinees.add(p, type, direction);
  }

  @Override
  public void removeParticipant(final Participant p) {
    _joinees.remove(p);
  }

  @Override
  public Joint join(final Participant other, final JoinType type, final Direction direction)
      throws IllegalStateException {
    synchronized (this) {
      checkState();
      if (_joinees.contains(other)) {
        return new JointImpl(_context.getExecutor(), new JointImpl.DummyJoinWorker(MixerImpl.this, other));
      }
    }

    if (other instanceof Call) {
      Joint joint = null;
      if (isClampDtmf(null)) {
        joint = other.join(_adapterParticipant, type, direction);
      }
      else {
        joint = other.join(this, type, direction);
      }

      return joint;
    }
    else {
      if (!(other.getMediaObject() instanceof Joinable)) {
        throw new IllegalArgumentException("MediaObject is't joinable.");
      }
      return new JointImpl(_context.getExecutor(), new JoinWorker() {
        @Override
        public JoinCompleteEvent call() throws Exception {
          JoinCompleteEvent event = null;
          try {
            synchronized (MixerImpl.this) {
              if (MixerImpl.this.isClampDtmf(null)) {
                _adapter.join(direction, (Joinable) other.getMediaObject());
              }
              else {
                _mixer.join(direction, (Joinable) other.getMediaObject());
              }
              _joinees.add(other, type, direction);
              ((ParticipantContainer) other).addParticipant(MixerImpl.this, type, direction);
              event = new JoinCompleteEvent(MixerImpl.this, other, Cause.JOINED);
            }
          }
          catch (final Exception e) {
            event = new JoinCompleteEvent(MixerImpl.this, other, Cause.ERROR, e);
            throw new MediaException(e);
          }
          finally {
            MixerImpl.this.dispatch(event);
          }
          return event;
        }

        @Override
        public boolean cancel() {
          return false;
        }
      });
    }
  }

  @Override
  public synchronized void unjoin(final Participant p) {
    if (!_joinees.contains(p)) {
      return;
    }
    _joinees.remove(p);
    if (p.getMediaObject() instanceof Joinable) {
      try {
        _mixer.unjoin((Joinable) p.getMediaObject());
      }
      catch (final Exception e) {
        LOG.warn("", e);
      }
    }
    p.unjoin(this);
  }

  @Override
  public MediaObject getMediaObject() {
    return _mixer;
  }

  @Override
  public JoinableStream getJoinableStream(final StreamType arg0) throws MediaException, IllegalStateException {
    checkState();
    try {
      return _mixer.getJoinableStream(arg0);
    }
    catch (final MsControlException e) {
      throw new MediaException(e);
    }
  }

  @Override
  public JoinableStream[] getJoinableStreams() throws MediaException, IllegalStateException {
    checkState();
    try {
      return _mixer.getJoinableStreams();
    }
    catch (final MsControlException e) {
      throw new MediaException(e);
    }
  }

  protected void checkState() {
    if (_media == null) {
      throw new IllegalStateException();
    }
  }

  protected boolean isClampDtmf(Properties props) {
    boolean clampDTMF = _clampDtmf;
    if (props != null && props.get("playTones") != null) {
      if (Boolean.valueOf(props.getProperty("playTones"))) {
        clampDTMF = false;
      }
      else {
        clampDTMF = true;
      }
    }
    return clampDTMF;
  }

  @Override
  public Joint join(final Participant other, final JoinType type, final Direction direction, final Properties props) {
    synchronized (this) {
      checkState();
      if (_joinees.contains(other)) {
        return new JointImpl(_context.getExecutor(), new JointImpl.DummyJoinWorker(MixerImpl.this, other));
      }
    }

    if (other instanceof Call) {
      Joint joint = null;
      if (isClampDtmf(props)) {
        joint = other.join(_adapterParticipant, type, direction);
      }
      else {
        joint = other.join(this, type, direction);
      }

      return joint;
    }
    else {
      if (!(other.getMediaObject() instanceof Joinable)) {
        throw new IllegalArgumentException("MediaObject is't joinable.");
      }
      return new JointImpl(_context.getExecutor(), new JoinWorker() {
        @Override
        public JoinCompleteEvent call() throws Exception {
          JoinCompleteEvent event = null;
          try {
            synchronized (MixerImpl.this) {
              if (MixerImpl.this.isClampDtmf(props)) {
                _adapter.join(direction, (Joinable) other.getMediaObject());
              }
              else {
                _mixer.join(direction, (Joinable) other.getMediaObject());
              }
              _joinees.add(other, type, direction);
              ((ParticipantContainer) other).addParticipant(MixerImpl.this, type, direction);
              event = new JoinCompleteEvent(MixerImpl.this, other, Cause.JOINED);
            }
          }
          catch (final Exception e) {
            event = new JoinCompleteEvent(MixerImpl.this, other, Cause.ERROR, e);
            throw new MediaException(e);
          }
          finally {
            MixerImpl.this.dispatch(event);
          }
          return event;
        }

        @Override
        public boolean cancel() {
          return false;
        }
      });
    }
  }

  class MyMixerAdapter implements Mixer, ParticipantContainer {

    @Override
    public MediaService getMediaService() {
      return MixerImpl.this.getMediaService();
    }

    @Override
    public Joint join(Participant other, JoinType type, Direction direction, Properties props) {
      return MixerImpl.this.join(other, type, direction, props);
    }

    @Override
    public JoinableStream getJoinableStream(StreamType value) {
      JoinableStream result = null;

      try {
        result = MixerImpl.this._adapter.getJoinableStream(value);
      }

      catch (final MsControlException e) {
        throw new MediaException(e);
      }
      return result;
    }

    @Override
    public JoinableStream[] getJoinableStreams() {
      JoinableStream[] result = null;

      try {
        result = MixerImpl.this._adapter.getJoinableStreams();
      }

      catch (final MsControlException e) {
        throw new MediaException(e);
      }
      return result;
    }

    @Override
    public void disconnect() {
      MixerImpl.this.disconnect();
    }

    @Override
    public Endpoint getAddress() {
      return MixerImpl.this.getAddress();
    }

    @Override
    public MediaObject getMediaObject() {
      return MixerImpl.this._adapter;
    }

    @Override
    public Participant[] getParticipants() {
      return MixerImpl.this.getParticipants();
    }

    @Override
    public Participant[] getParticipants(Direction direction) {
      return MixerImpl.this.getParticipants(direction);
    }

    @Override
    public Joint join(Participant other, JoinType type, Direction direction) {
      return MixerImpl.this.join(other, type, direction);
    }

    @Override
    public void unjoin(Participant other) {
      MixerImpl.this.unjoin(other);
    }

    @Override
    public void addExceptionHandler(ExceptionHandler... handlers) {
      MixerImpl.this.addExceptionHandler(handlers);
    }

    @Override
    public void addListener(EventListener<?> listener) {
      MixerImpl.this.addListener(listener);
    }

    @Override
    public <E extends Event<?>, T extends EventListener<E>> void addListener(Class<E> type, T listener) {
      MixerImpl.this.addListener(type, listener);
    }

    @Override
    public void addListeners(EventListener<?>... listeners) {
      MixerImpl.this.addListeners(listeners);
    }

    @Override
    public <E extends Event<?>, T extends EventListener<E>> void addListeners(Class<E> type, T... listener) {
      MixerImpl.this.addListeners(type, listener);
    }

    @Override
    public void addObserver(Observer observer) {
      MixerImpl.this.addObserver(observer);
    }

    @Override
    public void addObservers(Observer... observers) {
      MixerImpl.this.addObservers(observers);
    }

    @Override
    public <S extends EventSource, T extends Event<S>> Future<T> dispatch(T event) {
      return MixerImpl.this.dispatch(event);
    }

    @Override
    public <S extends EventSource, T extends Event<S>> Future<T> dispatch(T event, Runnable afterExec) {
      return MixerImpl.this.dispatch(event, afterExec);
    }

    @Override
    public ApplicationContext getApplicationContext() {
      return MixerImpl.this.getApplicationContext();
    }

    @Override
    public String getApplicationState() {
      return MixerImpl.this.getApplicationState();
    }

    @Override
    public String getApplicationState(String FSM) {
      return MixerImpl.this.getApplicationState(FSM);
    }

    @Override
    public void removeListener(EventListener<?> listener) {
      MixerImpl.this.removeListener(listener);
    }

    @Override
    public void removeObserver(Observer listener) {
      MixerImpl.this.removeObserver(listener);
    }

    @Override
    public void setApplicationState(String state) {
      MixerImpl.this.setApplicationState(state);
    }

    @Override
    public void setApplicationState(String FSM, String state) {
      MixerImpl.this.setApplicationState(FSM, state);
    }

    @Override
    public String getId() {
      return MixerImpl.this.getId();
    }

    @Override
    public Object getAttribute(String name) {
      return MixerImpl.this.getAttribute(name);
    }

    @Override
    public Map<String, Object> getAttributeMap() {
      return MixerImpl.this.getAttributeMap();
    }

    @Override
    public void setAttribute(String name, Object value) {
      MixerImpl.this.setAttribute(name, value);
    }

    @Override
    public void addParticipant(Participant p, JoinType type, Direction direction) {
      MixerImpl.this.addParticipant(p, type, direction);
    }

    @Override
    public void removeParticipant(Participant p) {
      MixerImpl.this.removeParticipant(p);
    }
  }
}
