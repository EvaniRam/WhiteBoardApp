/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujixerox.whiteboardapp;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Set;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
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
    
    private static Set<Session> session_peers=Collections.synchronizedSet(new HashSet<Session>());
    private static HashMap<Session,Peer> map_peers=new HashMap<>();
    private static int peer_id=0;
    private static PriorityQueue<Integer> reclaimed_peer_id_queue=new PriorityQueue<>();

    @OnMessage
    public String onMessage(Session session,String message) {
        
        
        System.out.println("got message from client: "+message);
       
        Peer peer=map_peers.get(session);
        
        if(peer==null)
        {
            System.out.println("cannot find peer in the session list, message ignored!");
            return null;
        }
        
        ProcessMessage(peer,message,session);
        
       
        return null;
    }

    @OnClose
    public void onClose(Session session,CloseReason reason){
        
        System.out.println("received close request");
        
        
        //first see if this peer has logged out out before
        if(map_peers.containsKey(session))
        {
            
           
            Peer peer=map_peers.get(session);
            
            if(peer.id>0)
            {
                System.out.println("The peer has not logged out, log out first!");
                
                //send the broadcast message to other peers
                JsonObject oobj=Json.createObjectBuilder()
                .add("action", "peer_logout")
                .add("id", peer.id)
                .add("name",peer.name).build();
        
                BroadcastMessage(oobj.toString(),session);
                
                 //put the id into the reclaimed peer id queue
                this.reclaimed_peer_id_queue.add(peer.id);
                
                peer.id=-1;
                peer.name=null;
                peer.status=Peer.Status.STATUS_IDLE;
            }
            
            
        }
        
        
        
        try{
        session.close();
        }catch (IOException e)
        {
            e.printStackTrace();
        }
        session_peers.remove(session);
        map_peers.remove(session);
        
    }

    @OnOpen
    public void onOpen(Session session,EndpointConfig config) {
        
        session_peers.add(session);
        map_peers.put(session, new Peer());
    }
    
    @OnError
    public void onError(Session session, Throwable error)
    {
        
    }
    
    public void ProcessMessage(Peer peer,String message,Session session)
    {
        JsonReader reader=Json.createReader(new StringReader(message));
        JsonObject jsonst=reader.readObject();
        String action=jsonst.getString("action");
        if(action.equals("login"))
        {
           PeerLogin(session,peer,jsonst); 
        }
        else if(action.equals("logout"))
        {
           PeerLogout(session,peer,jsonst);
        }
        else if(action.equals("update_status"))
        {
           PeerUpdateStatus(session,peer,jsonst);         
        }
        
    }
    
    public void BroadcastMessage(String msg) 
    {
        for(Session session: session_peers)
        {
            try{
            session.getBasicRemote().sendText(msg);
            }catch (IOException e)
            {
                e.printStackTrace();
            }
        }
            
    }
    
    public void BroadcastMessage(String msg,Session this_session)
    {
        for(Session session: session_peers)
        {
            
            if(session.equals(this_session))
                continue;
            
            try{
            session.getBasicRemote().sendText(msg);
            }catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
    
    
    public void SendMessage(String msg,Session session)
    {
        try{
        session.getBasicRemote().sendText(msg);
        }catch(IOException e)
        {
            e.printStackTrace();
        }
        
        System.out.println("message is sent: "+msg);
    }
    
    
    public void PeerLogin(Session session,Peer peer,JsonObject json)
    {
        if(peer.id>0)
            return;
        
        System.out.println("received peer login request");
        
        
        int candiate_id;
        if(!this.reclaimed_peer_id_queue.isEmpty())
        {
            candiate_id=this.reclaimed_peer_id_queue.poll();
        }
        else
        {
            peer_id++;
            candiate_id=peer_id;
        }
        
        
        peer.id=candiate_id;
        peer.status=Peer.Status.STATUS_IDLE;
        
        String name=json.getString("name");
        peer.name=name;
        
        String platform=json.getString("platform");
        peer.platform=platform;
        
        String udp_enabled=json.getString("udp");
        if(udp_enabled.equalsIgnoreCase("true"))
        {
            peer.udp_enabled=true;
        }
        
        //send a response message to client
        JsonObject obj=Json.createObjectBuilder()
                .add("action", "login")
                .add("id", Integer.toString(peer.id)).build();
        
        
        SendMessage(obj.toString(),session);
        
        
        SendOnlinePeers(session);
        
        
        //send the broadcast message to other peers
        String status=peer.status==Peer.Status.STATUS_IDLE?"idle":"busy";
        
        JsonObject oobj=Json.createObjectBuilder()
                .add("action", "peer_login")
                .add("id", Integer.toString(peer_id))
                .add("name",peer.name)
                .add("platform",peer.platform)
                .add("status", status)
                .add("udp",Boolean.toString(peer.udp_enabled)).build();
        
        BroadcastMessage(oobj.toString(),session);
        
        System.out.println("Login: id "+peer.id+" name: "+peer.name);
        
    }
    
    public void PeerLogout(Session session,Peer peer,JsonObject json)
    {
        if(peer.id<0)
            return;
        
        
       
         //send a response message to client
        JsonObject obj=Json.createObjectBuilder()
                .add("action", "logout").build();
        
        
        SendMessage(obj.toString(),session);
        
        //send the broadcast message to other peers
        JsonObject oobj=Json.createObjectBuilder()
                .add("action", "peer_logout")
                .add("id", peer.id)
                .add("name",peer.name).build();
        
        BroadcastMessage(oobj.toString(),session);
        
        
        //put the id into the reclaimed peer id queue
        this.reclaimed_peer_id_queue.add(peer.id);
        
        peer.id=-1;
        peer.name=null;
        peer.status=Peer.Status.STATUS_IDLE;
        
       // map_peers.remove(session);
        
    }
    
    public void PeerUpdateStatus(Session session,Peer peer,JsonObject json)
    {
        
        //update status first
        boolean status_changed=false;
        Peer.Status old_status=peer.status;
        Peer.Status new_status;
        
        
        
        if(json.getString("status").equalsIgnoreCase("busy"))
        {
            new_status=Peer.Status.STATUS_BUSY;
        }
        else
            new_status=Peer.Status.STATUS_IDLE;
        
        if(new_status!=old_status)
            status_changed=true;
            
        
         //send a response message to client
        JsonObject obj=Json.createObjectBuilder()
                .add("action", "update_status").build();
        
        
        SendMessage(obj.toString(),session);
        
        if(!status_changed)
            return;
        
        peer.status=new_status;
        
        //only send broadcast if the status is really updated to a new one
        //send the broadcast message to other peers
        JsonObject oobj=Json.createObjectBuilder()
                .add("action", "peer_status_update")
                .add("id", peer.id)
                .add("status",json.getString("status")).build();
        
        BroadcastMessage(oobj.toString(),session);
    }
    
    public void SendOnlinePeers(Session session)
    {
        
        for(Session local_session:session_peers)
        {
            if(local_session==session)
                continue;
            
            Peer peer=map_peers.get(local_session);
            if(peer==null)
            {
                System.out.println("Fatal Error! Peer does not exist!");
                continue;
            }
            
            //check if the peer is online
            if(peer.id<0)
                continue;
            
            //send this peer message to client
            String status=peer.status==Peer.Status.STATUS_IDLE?"idle":"busy";
            
            JsonObject obj=Json.createObjectBuilder()
                .add("action", "peer_online")
                .add("id", Integer.toString(peer_id))
                .add("name",peer.name)
                .add("platform",peer.platform)
                .add("status", status)
                .add("udp",Boolean.toString(peer.udp_enabled)).build();
            
           SendMessage(obj.toString(),session); 
        }
    }
    
}
