/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package freenet.darknetapp;

import freenet.support.io.LineReadingInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Illutionist
 */
public class DarknetAppConnectionHandler {
    private Socket socket;
    private static String REQUEST_CERTIFICATE = "Certificate";
    private static String REQUEST_HOME_REFERENCE = "HomeReference";
    private static String REQUEST_PUSH_REFERENCE = "PushReference";
    private static String REQUEST_END_MESSAGE = "End";
    private static String REQUEST_CLOSE_CONNECTION = "CloseConnection";
    private OutputStream out;
    private LineReadingInputStream input;
    
    public DarknetAppConnectionHandler(Socket sock) {
      this.socket = sock; 
    }
    private boolean process(String command) throws IOException {
        boolean done = false;
        if (command==null) return done;
        else if (command.equals(REQUEST_CERTIFICATE)) {
            out.write(("Check"+'\n').getBytes("UTF-8"));
            done =true;
        }
        else if (command.equals(REQUEST_HOME_REFERENCE)) {
            out.write((DarknetAppServer.noderef+'\n').getBytes("UTF-8"));
            done = true;
        }
        else if (command.equals(REQUEST_PUSH_REFERENCE)) {
            String friendsRef = input.readLine(32768, 128, true); //NodeRef
            processFriendsRefernces(friendsRef);
            done = true;
        }
        else if (command.equals(REQUEST_CLOSE_CONNECTION)) {            
            System.out.println("done");
            done = false;
        }
        return done;
    }
    public void processConnection() {
        try {
            InputStream is = new BufferedInputStream(socket.getInputStream(), 4096);
            LineReadingInputStream lis = new LineReadingInputStream(is);
            out = socket.getOutputStream();
            String command;
            boolean done = true;
            while (done) {
                    command = lis.readLine(32768, 128, true);
                    done = process(command);
            }
        } catch (IOException ex) {
            finish();
        }

    }
    public void finish() {
        try {
            if (socket!=null && !socket.isClosed()) {
                //socket.shutdownInput();
                //socket.shutdownOutput();
                socket.close();
            }
            if(input!=null) input.close();
            if (out!=null) out.close();
            //socket = null;
            input =null;
            out = null;
        } catch (IOException ex) {
            Logger.getLogger(DarknetAppConnectionHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    public static void handle(Socket sock) {
        DarknetAppConnectionHandler context = new DarknetAppConnectionHandler(sock);
        context.processConnection();
        context.finish();
    }

    private void processFriendsRefernces(String friendsRef) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
          
}
