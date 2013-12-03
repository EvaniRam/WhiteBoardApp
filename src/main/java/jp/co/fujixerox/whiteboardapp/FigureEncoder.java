/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujixerox.whiteboardapp;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

/**
 *
 * @author lapmore
 */
public class FigureEncoder implements Encoder.Text<Figure>{

    @Override
    public String encode(Figure figure) throws EncodeException {
        return figure.getJson().toString();
    }

    @Override
    public void init(EndpointConfig config) {
       System.out.println("encoder init");
    }

    @Override
    public void destroy() {
        System.out.println("encoder destroy");
    }
    
    
}
