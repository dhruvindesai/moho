/**
 * Copyright 2010-2011 Voxeo Corporation
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

package com.voxeo.moho.media.input;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.media.mscontrol.Parameter;
import javax.media.mscontrol.Parameters;
import javax.media.mscontrol.resource.RTC;
import javax.media.mscontrol.resource.Resource;

import com.voxeo.moho.media.BeepParameters;
import com.voxeo.moho.media.EnergyParameters;
import com.voxeo.moho.media.InputMode;

/**
 * Media command to recognize the input based on a set of grammars.
 * 
 * @author wchen
 */
public class InputCommand implements Parameters {

  protected int _signalNumber = -1;

  protected Grammar[] _grammars = new Grammar[0];

  protected float _minConfidence = 0.3f;

  protected float _sensitivity = 0.5f;

  protected long _initialTimeout = Resource.FOREVER;

  protected long _interSigTimeout = Resource.FOREVER;

  protected long _maxTimeout = Resource.FOREVER;

  protected boolean _record = false;

  protected URI _recordURI = null;

  protected boolean _buffering = true;

  protected Parameters _parameters;

  protected RTC[] _rtcs;

  protected Map<Parameter, Object> _parametersExt = new HashMap<Parameter, Object>();

  protected Set<RTC> _rtcsExt = new HashSet<RTC>();

  protected String _recognizer;

  protected Character _terminator;

  protected InputMode _inputMode;

  protected boolean _dtmfHotword = false;

  protected boolean _dtmfTypeahead = false;

  /**
   * if true, every DTMF (or word?) received generates an event
   */
  protected boolean _supervised = true;

  /**
   * Defines the required length of silence following user speech after which a
   * recognizer finalizes a result. The incomplete timeout applies when the
   * speech prior to the silence is an incomplete match of all active grammars.
   */
  protected long _speechIncompleteTimeout;

  /**
   * Defines the length of silence required following user speech before the
   * speech recognizer finalizes a result (either accepting it or generating a
   * nomatch event).
   */
  protected long _speechCompleteTimeout;

  protected boolean _flushBuffer;

  protected boolean _autoreset;

  protected EnergyParameters _energyParams = new EnergyParameters();

  protected BeepParameters _beepParams = new BeepParameters();

  protected boolean _ignorePromptFailure;

  /**
   * @param grammers
   *          can be simple string or string that starts with "#JSGF". if the
   *          string starts with "#JSGF", a JSGF grammar will be created.
   * @deprecated Grammar type 'guessing' has been deprecated. Supply a Grammar
   *             instance instead.
   */
  public InputCommand(String grammer) {
    if (grammer == null || grammer.length() == 0) {
      throw new IllegalArgumentException();
    }
    _grammars = new Grammar[] {Grammar.create(grammer)};
  }

  /**
   * Construct command with multiple grammars
   * 
   * @param grammars
   */
  public InputCommand(final Grammar... grammars) {
    if (grammars != null && grammars.length > 0) {
      _grammars = grammars;
    }
  }

  /**
   * @return the timeout to determine the end of DTMF input
   */
  public long getInterDigitsTimeout() {
    return _interSigTimeout;
  }

  /**
   * Set the inter digits timeout for DTMF.
   * <p>
   * The defaut value is {@link Resource#FOREVER} which means this timeout is
   * disabled.
   * 
   * @param time
   *          the timeout value in millisecond.
   */
  public void setInterDigitsTimeout(final long time) {
    _interSigTimeout = time;
  }

  public boolean isBuffering() {
    return _buffering;
  }

  public void setBuffering(final boolean buffering) {
    _buffering = buffering;
  }

  /**
   * @return the number of DMTF digits this command expects. Once reached, this
   *         command is considered as complete and
   *         {@link com.voxeo.moho.event.InputCompleteEvent InputCompleteEvent}
   *         will fire.
   */
  public int getNumberOfDigits() {
    return _signalNumber;
  }

  /**
   * Set the number of DTMF digits this command expects. By default, the value
   * is -1 which means unlimited.
   * 
   * @param num
   *          a negative number means unlimited.
   */
  public void setNumberOfDigits(final int num) {
    if (num < 0) {
      _signalNumber = -1;
    }
    else {
      _signalNumber = num;
    }
  }

  /**
   * @return the grammars for this command.
   */
  public Grammar[] getGrammars() {
    return _grammars;
  }

  /**
   * @return the minimum confidence required for the recognizer to recognize the
   *         speech based on the grammar.
   */
  public float getMinConfidence() {
    return _minConfidence;
  }

  public boolean isRecord() {
    return _record;
  }

  public void setRecord(final boolean flag) {
    _record = flag;
  }

  public URI getRecordURI() {
    return _recordURI;
  }

  public void setRecordURI(final URI uri) {
    _recordURI = uri;
  }

  public float getSensitivity() {
    return _sensitivity;
  }

  /**
   * Set how sensitive the interpreter is to loud versus quiet input.
   * <p>
   * Adjusting the default value of <code>0.5</code> upwards will make the
   * interpeter more sensitive to quiet input, while adjusting this value
   * downwards will make it less sensitive to quiet input.
   * 
   * @param _sensitivity
   */
  public void setSensitivity(float _sensitivity) {
    this._sensitivity = _sensitivity;
  }

  /**
   * Set the minimum confidence required for the recognizer to recognize the
   * speech based on the grammar.
   * <p>
   * If one were to change the default value of <code>0.3</code> to
   * <code>0.1</code>, then any utterance would be matched, while if it were to
   * be adjusted to <code>1.0</code>, then virtually every user utterance would
   * be considered a nomatch.
   * 
   * @param confidence
   *          a float between 0 and 1.
   */
  public void setMinConfidence(final float confidence) {
    if (confidence < 0) {
      throw new IllegalArgumentException("Confidence must be greater than 0.");
    }
    if (confidence > 1) {
      throw new IllegalArgumentException("Confidence must be less than 1.");
    }
    _minConfidence = confidence;
  }

  /**
   * @return the timeout to determine no digits will be entered.
   */
  public long getInitialTimeout() {
    return _initialTimeout;
  }

  /**
   * Set the timeout value to determine no digits will be entered.
   * <p>
   * The defaut value is {@link Resource#FOREVER} which means this timeout is
   * disabled.
   * 
   * @param time
   *          the timeout value in milliseconds.
   */
  public void setInitialTimeout(final long time) {
    _initialTimeout = time;
  }

  /**
   * @return the max time to wait for the completion of the input.
   */
  public long getMaxTimeout() {
    return _maxTimeout;
  }

  /**
   * Set the max time to wait for the completion of the input
   * <p>
   * The defaut value is {@link Resource#FOREVER} which means this timeout is
   * disabled.
   * 
   * @param time
   *          the time in milliseconds.
   */
  public void setMaxTimeout(final long time) {
    _maxTimeout = time;
  }

  public void setSupervised(final boolean supervised) {
    _supervised = supervised;
  }

  public boolean isSupervised() {
    return _supervised;
  }

  /**
   * @return the name of the speech recognizer will be used. The interpretation
   *         is JSR 309 driver specific.
   */
  public String getRecognizer() {
    return _recognizer;
  }

  /**
   * Set the name of the speech recognizer. *
   * <p>
   * The defaut value is platform specific. For example, in Prism it defaults to
   * <code>en-us</code>.
   * 
   * @param recognizer
   */
  public void setRecognizer(String recognizer) {
    this._recognizer = recognizer;
  }

  /**
   * @return The terminating character of the input.
   */
  public Character getTerminator() {
    return _terminator;
  }

  /**
   * Set the terminating character of the input. No default value.
   * 
   * @param termChar
   *          one of the valid DTMF input on the phone pad.
   */
  public void setTerminator(Character termChar) {
    this._terminator = termChar;
  }

  /**
   * @return the Input Mode of this input.
   */
  public InputMode getInputMode() {
    return _inputMode;
  }

  /**
   * Set Input Mode of this input.
   * <p>
   * The defaut value is {@link InputMode#ANY}.
   * 
   * @param inputMode
   */
  public void setInputMode(InputMode inputMode) {
    this._inputMode = inputMode;
  }

  @Deprecated
  public Parameters getParameters() {
    return _parameters;
  }

  @Deprecated
  public void setParameters(Parameters parameters) {
    _parameters = parameters;
  }

  @Deprecated
  public RTC[] getRtcs() {
    return _rtcs;
  }

  @Deprecated
  public void setRtcs(RTC[] rtcs) {
    _rtcs = rtcs;
  }

  // method for RTC
  public boolean addRTC(RTC o) {
    return _rtcsExt.add(o);
  }

  public boolean addAllRTC(Collection<? extends RTC> c) {
    return _rtcsExt.addAll(c);
  }

  public void removeAllRTC() {
    _rtcsExt.clear();
  }

  public boolean removeRTC(Object o) {
    return _rtcsExt.remove(o);
  }

  public Set<RTC> getAllRTC() {
    return _rtcsExt;
  }

  // method for Parameters
  @Override
  public void clear() {
    _parametersExt.clear();
  }

  @Override
  public boolean containsKey(Object key) {
    return _parametersExt.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return _parametersExt.containsValue(value);
  }

  @Override
  public Set<java.util.Map.Entry<Parameter, Object>> entrySet() {
    return _parametersExt.entrySet();
  }

  @Override
  public Object get(Object key) {
    return _parametersExt.get(key);
  }

  @Override
  public boolean isEmpty() {
    return _parametersExt.isEmpty();
  }

  @Override
  public Set<Parameter> keySet() {
    return _parametersExt.keySet();
  }

  @Override
  public Object put(Parameter key, Object value) {
    return _parametersExt.put(key, value);
  }

  @Override
  public void putAll(Map<? extends Parameter, ? extends Object> t) {
    _parametersExt.putAll(t);
  }

  @Override
  public Object remove(Object key) {
    return _parametersExt.remove(key);
  }

  @Override
  public int size() {
    return _parametersExt.size();
  }

  @Override
  public Collection<Object> values() {
    return _parametersExt.values();
  }

  public boolean isDtmfHotword() {
    return _dtmfHotword;
  }

  public void setDtmfHotword(boolean dtmfHotword) {
    _dtmfHotword = dtmfHotword;
  }

  public boolean isDtmfTypeahead() {
    return _dtmfTypeahead;
  }

  public void setDtmfTypeahead(boolean dtmfTypeahead) {
    _dtmfTypeahead = dtmfTypeahead;
  }

  public long getSpeechIncompleteTimeout() {
    return _speechIncompleteTimeout;
  }

  /**
   * Change the setting of when, after a caller pauses in mid-utterance, the
   * interpreter will start to process said utterance.
   * <p>
   * After the period of time specified, the interpreter will process the
   * utterance and return either a valid grammar match, or a nomatch condition.
   * <p>
   * The default value is platform specific.
   * 
   * @param speechIncompleteTimeout
   */
  public void setSpeechIncompleteTimeout(long speechIncompleteTimeout) {
    this._speechIncompleteTimeout = speechIncompleteTimeout;
  }

  public long getSpeechCompleteTimeout() {
    return _speechCompleteTimeout;
  }

  /**
   * Set the length of time after a user utterance where the interpreter will
   * consider the utterance complete.
   * <p>
   * The default value is platform specific.
   * 
   * @param speechCompleteTimeout
   */
  public void setSpeechCompleteTimeout(long speechCompleteTimeout) {
    this._speechCompleteTimeout = speechCompleteTimeout;
  }

  public boolean isFlushBuffer() {
    return _flushBuffer;
  }
  public void setFlushBuffer(boolean flushBuffer) {
    this._flushBuffer = flushBuffer;
  }

  public boolean getAutoReset() {
    return this._autoreset;
  }

  public void setAutoRest(boolean autoreset) {
    this._autoreset = autoreset;
  }

  public void setEnergyParameters(Long finalSilence, Long maxSilence, Long maxSpeechDuration, Long minSpeechDuration,
      Integer minVolume) {
    if (finalSilence != null) {
      _energyParams.setFinalSilence(finalSilence);
    }
    if (maxSilence != null) {
      _energyParams.setMaxSilence(maxSilence);
    }
    if (maxSpeechDuration != null) {
      _energyParams.setMaxSpeechDuration(maxSpeechDuration);
    }
    if (minSpeechDuration != null) {
      _energyParams.setMinSpeechDuration(minSpeechDuration);
    }
    if (minVolume != null) {
      _energyParams.setMinVolume(minVolume);
    }
  }

  public EnergyParameters getEnergyParameters() {
    return _energyParams;
  }

  public void setBeepParameters(Long onTime, Long offTime, Long minFrequency, Long maxFrequency,
      Float frequencyTolerance, Float monotonicity, Float signalToNoise, Float highMonotonicity, Integer minPower,
      Long maxNoiseTime, Long maxConsequitiveNoiseTime) {
    if (onTime != null) {
      _beepParams.setOnTime(onTime);
    }
    if (offTime != null) {
      _beepParams.setOffTime(offTime);
    }
    if (minFrequency != null) {
      _beepParams.setMinFrequency(minFrequency);
    }
    if (maxFrequency != null) {
      _beepParams.setMaxFrequency(maxFrequency);
    }
    if (monotonicity != null) {
      _beepParams.setMonotonicity(monotonicity);
    }
    if (signalToNoise != null) {
      _beepParams.setSignalToNoise(signalToNoise);
    }
    if (highMonotonicity != null) {
      _beepParams.setHighMonotonicity(highMonotonicity);
    }
    if (minPower != null) {
      _beepParams.setMinPower(minPower);
    }
    if (maxNoiseTime != null) {
      _beepParams.setMaxNoiseTime(maxNoiseTime);
    }
    if (maxConsequitiveNoiseTime != null) {
      _beepParams.setMaxConsecutiveNoiseTime(maxConsequitiveNoiseTime);
    }
  }

  public BeepParameters getBeepParameters() {
    return _beepParams;
  }

  public boolean isIgnorePromptFailure() {
    return _ignorePromptFailure;
  }

  public void setIgnorePromptFailure(boolean ignorePromptFailure) {
    this._ignorePromptFailure = ignorePromptFailure;
  }
}
