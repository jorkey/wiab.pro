/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.wave.client.gadget.renderer;

import com.google.gwt.core.client.JavaScriptObject;

/**
 * Utility functions for sending information to Wave gadget using RPC.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class GadgetRpcSender {
  
  /**
   * Sends setPref RPC to the gadget.
   *
   * @param target the gadget frame ID.
   * @param name name of the preference to set.
   * @param value value of the preference.
   */
  public static native void sendGadgetPrefRpc(String target, String name, String value) /*-{
    try {
      $wnd.gadgets.rpc.call(target, 'set_pref', null, 0, name, value);
    } catch (e) {
      // HACK(user): Ignoring any failure for now.
      @org.waveprotocol.wave.client.gadget.GadgetLog::log(Ljava/lang/String;)
      ('set_pref RPC failed');
    }
  }-*/;
  
  /**
   * Sends initialization RPC to Podium gadget.
   *
   * @param target the gadget frame ID.
   * @param id Podium ID of this client.
   * @param otherId Podium ID of the opponent client.
   */
  public static native void sendPodiumOnInitializedRpc(String target, String id, String otherId) /*-{
    try {
      $wnd.gadgets.rpc.call(target, 'onInitialized', null, id, otherId);
    } catch (e) {
      // HACK(user): Ignoring any failure for now.
      @org.waveprotocol.wave.client.gadget.GadgetLog::log(Ljava/lang/String;)
      ('onInitialized RPC failed');
    }
  }-*/;
  
  /**
   * Sends state change RPC to Podium gadget.
   *
   * @param target the gadget frame ID.
   * @param state Podium gadget state.
   */
  public static native void sendPodiumOnStateChangedRpc(String target, String state) /*-{
    try {
      $wnd.gadgets.rpc.call(target, 'onStateChanged', null, state);
    } catch (e) {
      // HACK(user): Ignoring any failure for now.
      @org.waveprotocol.wave.client.gadget.GadgetLog::log(Ljava/lang/String;)
      ('onStateChanged RPC failed');
    }
  }-*/;
  
  /**
   * Sends participant information to Wave gadget.
   *
   * @param target the gadget frame ID.
   * @param participants JSON string of Wavelet participants.
   */
  public static native void sendParticipantsRpc(String target, JavaScriptObject participants) /*-{
    try {
      $wnd.gadgets.rpc.call(target, 'wave_participants', null, participants);
    } catch (e) {
      // HACK(user): Ignoring any failure for now.
      @org.waveprotocol.wave.client.gadget.GadgetLog::log(Ljava/lang/String;)
      ('wave_participants RPC failed');
    }
  }-*/;

  /**
   * Sends gadget state to Wave gadget.
   *
   * @param target the gadget frame ID.
   * @param state JSON string of Gadget state.
   */
  public static native void sendGadgetStateRpc(String target, JavaScriptObject state) /*-{
    try {
      $wnd.gadgets.rpc.call(target, 'wave_gadget_state', null, state);
    } catch (e) {
      // HACK(user): Ignoring any failure for now.
      @org.waveprotocol.wave.client.gadget.GadgetLog::log(Ljava/lang/String;)
      ('wave_gadget_state RPC failed');
    }
  }-*/;

  /**
   * Sends private gadget state to Wave gadget.
   *
   * @param target the gadget frame ID.
   * @param state JSON string of Gadget state.
   */
  public static native void sendPrivateGadgetStateRpc(String target, JavaScriptObject state) /*-{
    try {
      $wnd.gadgets.rpc.call(target, 'wave_private_gadget_state', null, state);
    } catch (e) {
      // HACK(user): Ignoring any failure for now.
      @org.waveprotocol.wave.client.gadget.GadgetLog::log(Ljava/lang/String;)
      ('wave_private_gadget_state RPC failed');
    }
  }-*/;

  /**
   * Sends gadget mode to Wave gadget.
   *
   * @param target the gadget frame ID.
   * @param mode JSON string of Gadget state.
   */
  public static native void sendModeRpc(String target, JavaScriptObject mode) /*-{
    try {
      $wnd.gadgets.rpc.call(target, 'wave_gadget_mode', null, mode);
    } catch (e) {
      // HACK(user): Ignoring any failure for now.
      @org.waveprotocol.wave.client.gadget.GadgetLog::log(Ljava/lang/String;)
      ('wave_gadget_mode RPC failed');
    }
  }-*/;  
}
