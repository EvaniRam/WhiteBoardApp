/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujixerox.whiteboardapp;

import javax.websocket.Session;

/**
 *
 * @author haiyang
 */
public class Peer {
    
   
    public int id;
    public String name;
    public Status status;
    
    
    
    public Peer()
    {
        this.id=-1;
        
    }
    
    public enum Status
    {
        STATUS_IDLE,
        STATUS_BUSY
    }
    
    
   
    
}
