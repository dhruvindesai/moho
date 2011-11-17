package com.voxeo.moho.remote.impl;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

import com.rayo.client.RayoClient;
import com.rayo.client.XmppException;
import com.rayo.client.listener.StanzaListener;
import com.rayo.client.xmpp.stanza.IQ;
import com.rayo.client.xmpp.stanza.Message;
import com.rayo.client.xmpp.stanza.Presence;
import com.rayo.core.OfferEvent;
import com.voxeo.moho.CallableEndpoint;
import com.voxeo.moho.Endpoint;
import com.voxeo.moho.Participant;
import com.voxeo.moho.common.event.DispatchableEventSource;
import com.voxeo.moho.common.util.Utils.DaemonThreadFactory;
import com.voxeo.moho.remote.AuthenticationCallback;
import com.voxeo.moho.remote.MohoRemote;
import com.voxeo.moho.remote.MohoRemoteException;

@SuppressWarnings("deprecation")
public class MohoRemoteImpl extends DispatchableEventSource implements MohoRemote {

  protected static final Logger LOG = Logger.getLogger(MohoRemoteImpl.class);

  protected RayoClient _client;

  protected ThreadPoolExecutor _executor;

  protected Map<String, ParticipantImpl> _participants = new ConcurrentHashMap<String, ParticipantImpl>();

  protected Lock _participanstLock = new ReentrantLock();

  public MohoRemoteImpl() {
    super();
    // TODO make configurable
    int eventDispatcherThreadPoolSize = 10;
    _executor = new ThreadPoolExecutor(eventDispatcherThreadPoolSize, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(), new DaemonThreadFactory("MohoContext"));
    _executor.prestartAllCoreThreads();
    _dispatcher.setExecutor(_executor, false);
  }

  @Override
  public void disconnect() throws MohoRemoteException {
    try {
      Collection<ParticipantImpl> participants = _participants.values();
      for (Participant participant : participants) {
        participant.disconnect();
      }

      _participants.clear();
    }
    catch (Exception ex) {
      LOG.warn("", ex);
    }

    try {
      _client.disconnect();
    }
    catch (XmppException e) {
      throw new MohoRemoteException(e);
    }
    finally {
      _executor.shutdown();
    }
  }

  class MohoStanzaListener implements StanzaListener {

    @Override
    public void onIQ(IQ iq) {
      if (iq.getFrom() != null) {
        // dispatch the stanza to corresponding participant.
        JID fromJID = new JID(iq.getFrom());
        String id = fromJID.getNode();
        if (id != null) {
          ParticipantImpl participant = MohoRemoteImpl.this.getParticipant(id);
          if (participant != null) {
            participant.onRayoCommandResult(fromJID, iq);
          }
          else {
            LOG.error("Can't find participant for rayo event:" + iq);
          }
        }
        else {
          LOG.warn("Unprocessed IQ, No node ID:" + iq);
        }
      }
      else {
        LOG.warn("Unprocessed IQ, No from attribute:" + iq);
      }
    }

    @Override
    public void onMessage(Message message) {
      LOG.error("Received message from rayo:" + message);
    }

    @Override
    public void onPresence(Presence presence) {
      if (!presence.hasExtension()) {
        LOG.debug("MohoRemote Received presence without extension, discarding:" + presence);
        return;
      }

      JID fromJID = new JID(presence.getFrom());
      if (presence.getExtension().getStanzaName().equalsIgnoreCase("offer")) {
        OfferEvent offerEvent = (OfferEvent) presence.getExtension().getObject();

        IncomingCallImpl call = new IncomingCallImpl(MohoRemoteImpl.this, fromJID.getNode(),
            (CallableEndpoint) createEndpoint(offerEvent.getFrom()),
            (CallableEndpoint) createEndpoint(offerEvent.getTo()), offerEvent.getHeaders());

        MohoRemoteImpl.this.dispatch(call);
      }
      else {
        // dispatch the stanza to corresponding participant.
        String callID = fromJID.getNode();
        ParticipantImpl participant = MohoRemoteImpl.this.getParticipant(callID);
        if (participant != null) {
          participant.onRayoEvent(fromJID, presence);
        }
        else {
          if (presence.getShow() == null) {
            LOG.error("Can't find call for rayo event:" + presence);
          }
        }
      }
    }

    @Override
    public void onError(com.rayo.client.xmpp.stanza.Error error) {
      LOG.error("Got error" + error);
    }
  }

  public ParticipantImpl getParticipant(final String cid) {
    getParticipantsLock().lock();
    try {
      return _participants.get(cid);
    }
    finally {
      getParticipantsLock().unlock();
    }
  }

  protected void addParticipant(final ParticipantImpl participant) {
    _participants.put(participant.getId(), participant);
  }

  protected void removeParticipant(final String id) {
    getParticipantsLock().lock();
    try {
      _participants.remove(id);
    }
    finally {
      getParticipantsLock().unlock();
    }
  }

  public Lock getParticipantsLock() {
    return _participanstLock;
  }

  // TODO connection error handling, rayo-java-client should re-try
  // automatically
  @Override
  public void connect(AuthenticationCallback callback, String xmppServer, String rayoServer) {
    connect(callback.getUserName(), callback.getPassword(), callback.getRealm(), callback.getResource(), xmppServer,
        rayoServer);
  }

  @Override
  public void connect(String userName, String passwd, String realm, String resource, String xmppServer,
      String rayoServer) throws MohoRemoteException {
    connect(userName, passwd, realm, resource, xmppServer, rayoServer, 5);
  }

  @Override
  public void connect(String userName, String passwd, String realm, String resource, String xmppServer,
      String rayoServer, int timeout) throws MohoRemoteException {
    if (_client == null) {
      _client = new RayoClient(xmppServer, rayoServer);
      _client.addStanzaListener(new MohoStanzaListener());
    }
    try {
      _client.connect(userName, passwd, resource, timeout);
    }
    catch (XmppException e) {
      throw new MohoRemoteException("Error connecting to server", e);
    }
  }

  @Override
  public Endpoint createEndpoint(URI uri) {
    String uriString = uri.toString().trim();
    if (uriString.startsWith("mscontrol://")) {
      String mixerName = uriString.substring(12);
      if (mixerName.length() > 0) {
        return new MixerEndpointImpl(this, mixerName);
      }
      else {
        throw new IllegalArgumentException("Illegal mixer name:" + uri);
      }
    }
    else {
      return new CallableEndpointImpl(this, uri);
    }
  }

  public Executor getExecutor() {
    return _executor;
  }

  public RayoClient getRayoClient() {
    return _client;
  }
}
