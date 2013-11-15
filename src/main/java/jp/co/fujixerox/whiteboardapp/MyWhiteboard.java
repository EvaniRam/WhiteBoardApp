/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujixerox.whiteboardapp;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

/**
 *
 * @author haiyang
 */
@ServerEndpoint("/whiteboardendpoint")
public class MyWhiteboard {
    
    private static Set<Session> peers=Collections.synchronizedSet(new HashSet<Session>());

    @OnMessage
    public String onMessage(String message) {
        return null;
    }

    @OnClose
    public void onClose(Session peer) {
        peers.remove(peer);
    }

    @OnOpen
    public void onOpen(Session peer) {
        peers.add(peer);
    }
    
}
