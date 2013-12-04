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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    
    private ScheduledExecutorService heartbeatService;
    private Future<?> heartbeatTask;
    private Session session;
    private Peer self;

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
               System.out.println("The peer "+ peer.id+":"+peer.name+" has not logged out, log out first!");
                
                 //put the id into the reclaimed peer id queue
                 reclaimed_peer_id_queue.add(peer.id);
        
                 peer.id=-1;
                 peer.name=null;
                 peer.status=Peer.Status.STATUS_IDLE;
        
        
                 if(heartbeatTask!=null)
                 {
                   if(!heartbeatTask.isCancelled() && !heartbeatTask.isDone())
                   heartbeatTask.cancel(true);
                 }
            }
            
            
        }
        
        
        
        try{
        session.close();
        }catch (IOException e)
        {
            
            System.out.println("Error trying to close session!");
            e.printStackTrace();
        }
            session_peers.remove(session);
            map_peers.remove(session);
        
           
        
        if(heartbeatService!=null)
            heartbeatService.shutdown();
        
    }

    @OnOpen
    public void onOpen(Session session,EndpointConfig config) {
        
        session_peers.add(session);
        this.session=session;
        this.self=new Peer();
        map_peers.put(session, this.self);
        
        heartbeatService=Executors.newScheduledThreadPool(1);
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
        else if(action.equals("peer_msg"))
        {
           PeerRelayMessage(session,peer,jsonst);
        }
        else if(action.equals("heartbeat"))
        {
            
             if(heartbeatTask!=null)
              {
                if(!heartbeatTask.isCancelled() && !heartbeatTask.isDone())
                heartbeatTask.cancel(true);
              }
        
        
             heartbeatTask=heartbeatService.schedule(new HeartBeatTask(),18000, TimeUnit.MILLISECONDS);
        }
        
    }
    
    public void PeerRelayMessage(Session session,Peer peer,JsonObject json)
    {
        int destPeerID=Integer.parseInt(json.getString("to"));
        
        Session destSession=FindSessionbyID(destPeerID);
        
        if(destSession==null)
        {
            System.out.println("cannot find peer with id "+destPeerID);
            return;
        }
        
        
        SendMessage(json.toString(),destSession);
        
    }
    
    public Session FindSessionbyID(int destPeerID)
    {
        for (Entry thisEntry : map_peers.entrySet()) 
        {
            Peer peer=(Peer)thisEntry.getValue();
            Session session=(Session)thisEntry.getKey();
            if(peer.id==destPeerID)
                return session;   
        }
        
        return null;
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
        if(!reclaimed_peer_id_queue.isEmpty())
        {
            candiate_id=reclaimed_peer_id_queue.poll();
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
                .add("id", Integer.toString(peer.id))
                .add("name",peer.name)
                .add("platform",peer.platform)
                .add("status", status)
                .add("udp",Boolean.toString(peer.udp_enabled)).build();
        
        BroadcastMessage(oobj.toString(),session);
        
        System.out.println("Login: id "+peer.id+" name: "+peer.name);
        
        
        
        if(heartbeatTask!=null)
        {
            if(!heartbeatTask.isCancelled() && !heartbeatTask.isDone())
                heartbeatTask.cancel(true);
        }
        
        
        heartbeatTask=heartbeatService.schedule(new HeartBeatTask(),18000, TimeUnit.MILLISECONDS);
        
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
                .add("id", Integer.toString(peer.id))
                .add("name",peer.name).build();
        
        BroadcastMessage(oobj.toString(),session);
        
        
        //put the id into the reclaimed peer id queue
        reclaimed_peer_id_queue.add(peer.id);
        
        peer.id=-1;
        peer.name=null;
        peer.status=Peer.Status.STATUS_IDLE;
        
        
          if(heartbeatTask!=null)
          {
            if(!heartbeatTask.isCancelled() && !heartbeatTask.isDone())
                heartbeatTask.cancel(true);
          }
        
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
       /*   JsonObject obj=Json.createObjectBuilder()
                .add("action", "update_status").build();
        
        
        SendMessage(obj.toString(),session);
        */
        
        if(!status_changed)
            return;
        
        peer.status=new_status;
        
        //only send broadcast if the status is really updated to a new one
        //send the broadcast message to other peers
        JsonObject oobj=Json.createObjectBuilder()
                .add("action", "peer_status_update")
                .add("id", Integer.toString(peer.id))
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
                .add("id", Integer.toString(peer.id))
                .add("name",peer.name)
                .add("platform",peer.platform)
                .add("status", status)
                .add("udp",Boolean.toString(peer.udp_enabled)).build();
            
           SendMessage(obj.toString(),session); 
        }
    }
    
    private class HeartBeatTask implements Runnable{

        @Override
        public void run() {
            System.out.println("client "+self.id +" has been out of contact for long, assuming offline!");
            
            //assume the client has died, and force its logout
            //first see if this peer has logged out out before
          if(map_peers.containsKey(session))
          {
            
           
            Peer peer=map_peers.get(session);
            
            if(peer.id>0)
            {
                //System.out.println("The peer "+ peer.id+":"+peer.name+" has not logged out, log out first!");
                
                //send the broadcast message to other peers
                JsonObject oobj=Json.createObjectBuilder()
                .add("action", "peer_logout")
                .add("id", Integer.toString(peer.id))
                .add("name",peer.name).build();
        
                BroadcastMessage(oobj.toString(),session);
                
                 //put the id into the reclaimed peer id queue
                reclaimed_peer_id_queue.add(peer.id);
                
                peer.id=-1;
                peer.name=null;
                peer.status=Peer.Status.STATUS_IDLE;
            }
            
            
        }
        
        
        
        try{
        session.close();
        }catch (IOException e)
        {
            
            System.out.println("Error trying to close the session!");
            
            e.printStackTrace();
        }
        session_peers.remove(session);
        map_peers.remove(session);
        
        
       
        
    }
            
            
        }
    
    
    
}
