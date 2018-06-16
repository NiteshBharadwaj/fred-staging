/*
 * A server to communicate with darknet app on mobiles on the lines of SimpleToadletServer
 */
package freenet.darknetapp;

import freenet.config.InvalidConfigValueException;
import freenet.config.NodeNeedRestartException;
import freenet.config.SubConfig;
import freenet.crypt.BCModifiedSSL;
import freenet.crypt.SSL;
import freenet.io.BCSSLNetworkInterface;
import freenet.io.NetworkInterface;
import freenet.io.SSLNetworkInterface;
import freenet.l10n.NodeL10n;
import freenet.node.FSParseException;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.node.PrioRunnable;
import freenet.support.Executor;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.Logger.LogLevel;
import freenet.support.OOMHandler;
import freenet.support.SimpleFieldSet;
import freenet.support.api.BooleanCallback;
import freenet.support.api.IntCallback;
import freenet.support.api.StringCallback;
import freenet.support.io.NativeThread;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.logging.Level;
import org.tanukisoftware.wrapper.WrapperManager;


/**
 * @author Illutionist
 * TODO: A general server base class that extends to SimpleToadletServer, FCPServer, DarknetAppServer etc.
 *       Many functions are rewritten in this implementation as SimpleToadletServer was linked heavily with fproxy 
 */
public class DarknetAppServer implements Runnable {
    private boolean enabled = true;
    private final int DEFAULT_PORT = 7859; 
    private String allowedHosts;
    private String bindTo;
    private int port = DEFAULT_PORT;
    private Thread myThread;
    private final Executor executor;
    private Node node;
    private NodeClientCore core;
    private NetworkInterface networkInterface;
    private int maxDarknetAppConnections =10;	
    private int darknetAppConnections;
    private boolean finishedStartup;
    public static String noderef;
    
    // Newly exchanged peers that are neither accepted nor rejected
    public static int newDarknetPeersCount = 0;
    
    public static String filename = "TempPeersFromDarknetApp.prop";
    private static volatile boolean logMINOR;
	static {
            Logger.registerLogThresholdCallback(new LogThresholdCallback(){
                    @Override
                    public void shouldUpdate(){
                            logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
                    }
            });
	}
    public boolean isEnabled() {
        return myThread != null;
    }
    
    public void start() {
        if(myThread != null) {
            try {
                maybeGetNetworkInterface(true);
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(DarknetAppServer.class.getName()).log(Level.SEVERE, null, ex);
            }
                myThread.start();
                Logger.normal(this, "Starting DarknetAppServer on "+bindTo+ ':' +port);
                System.out.println("Starting DarknetAppServer on "+bindTo+ ':' +port);
        }
    }

    public DarknetAppServer(SubConfig darknetAppConfig, Node node, Executor executor) {
        this.executor = executor;
        this.node = node;
        this.core = node.clientCore;
        int configItemOrder = 0;
   
        //  allowedHosts - "*" to allow all, bindTo - "0.0.0.0" to accept on all interfaces
        darknetAppConfig.register("newDarknetPeersCount",0, configItemOrder++, true, true, "DarknetAppServer.newPeersCount", "DarknetAppServer.newPeersCountLong",
				new newDarknetPeersCallback(), false);
        DarknetAppServer.newDarknetPeersCount = darknetAppConfig.getInt("newDarknetPeersCount");
        darknetAppConfig.register("enabled", true, configItemOrder++, true, true, "DarknetAppServer.enabled", "DarknetAppServer.enabledLong",
				new  darknetAppEnabledCallback());
        this.enabled = darknetAppConfig.getBoolean("enabled");
        darknetAppConfig.register("port", DEFAULT_PORT, configItemOrder++, true, true, "DarknetAppServer.port", "DarknetAppServer.portLong",
				new darknetAppPortCallback(), false);
        this.port = darknetAppConfig.getInt("port");
        darknetAppConfig.register("allowedHosts", "*", configItemOrder++, true, true, "DarknetAppServer.allowedHosts", "DarknetAppServer.allowedHostsLong", 
                                new  darknetAppAllowedHostsCallback());				
        this.allowedHosts = darknetAppConfig.getString("allowedHosts");
        darknetAppConfig.register("bindTo", "0.0.0.0", configItemOrder++, true, true, "DarknetAppServer.bindTo", "DarknetAppServer.bindToLong",
				new darknetAppBindtoCallback());
        this.bindTo = darknetAppConfig.getString("bindTo"); 
        try {
            //always enable SSL - false to use plaintext
            maybeGetNetworkInterface(true);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(DarknetAppServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        configureFile();
        myThread = new Thread(this, "DarknetAppServer");
        myThread.setDaemon(true);
    }
    
    //Create File to save Temporary Noderefernces
    private void configureFile() {
        File file = new File(filename);
        if (!file.exists()) try {
            file.createNewFile();
        } catch (IOException ex) {
            Logger.error(this,"Error Creating File To Save Excahnged Nodereferences"+ex);
        }
    }
    public synchronized void changeNewDarknetPeersCount(int count) {
        changeNewDarknetPeersCount(count,node);
    }
    public static void changeNewDarknetPeersCount(int count,Node node) {
        try {
            SubConfig darknetAppConfig = node.config.get("darknetApp0");
            darknetAppConfig.set("newDarknetPeersCount", String.valueOf(count));
            node.config.store();
            newDarknetPeersCount = count;
            System.out.println(String.valueOf(darknetAppConfig.getInt("newDarknetPeersCount"))+"This is the one");
        } catch (InvalidConfigValueException ex) {
            java.util.logging.Logger.getLogger(DarknetAppServer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NodeNeedRestartException ex) {
            java.util.logging.Logger.getLogger(DarknetAppServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //Debugging purposes
    private void change(SubConfig subconfig) throws InvalidConfigValueException, NodeNeedRestartException {
        //subconfig.set("bindTo", "0.0.0.0");
        //subconfig.set("port",7873+"");
        //subconfig.set("allowedHosts","*");
    }
    
    //Modified from SimpleToadletServer to use BCSSLNetworkInterface instead of SSLNetworkInterface
    private void maybeGetNetworkInterface(boolean ssl) throws IOException {
        if (this.networkInterface!=null) return;
        if(ssl) {
            if (!BCModifiedSSL.available()) throw new IOException();
            System.out.println("now");
            System.out.println(BCModifiedSSL.getSelfSignedCertificatePin());
            this.networkInterface = BCSSLNetworkInterface.create(port, this.bindTo, allowedHosts, executor, false);
        } else {
            this.networkInterface = NetworkInterface.create(port, this.bindTo, allowedHosts, executor, true);
        }
    }
    
    
    public void finishStart() {
        synchronized(DarknetAppServer.class) {                
            //TODO: Change this 
            this.noderef = node.exportDarknetPublicFieldSet().toString();
            finishedStartup = true;
	}
    }

    private class newDarknetPeersCallback extends IntCallback {
        @Override
        public Integer get() {
            return newDarknetPeersCount;
        }

        @Override
        public void set(Integer val) throws InvalidConfigValueException, NodeNeedRestartException {
            synchronized(DarknetAppServer.class) {
                newDarknetPeersCount = val;
            }
        }
    }
    
    private class darknetAppBindtoCallback extends StringCallback  {
            @Override
            public String get() {
                    return bindTo;
            }

            @Override
            public void set(String bindTo) throws InvalidConfigValueException {
                    String oldValue = get();
                    if(!bindTo.equals(oldValue)) {
                            String[] failedAddresses = networkInterface.setBindTo(bindTo, false);
                            if(failedAddresses == null) {
                                    DarknetAppServer.this.bindTo = bindTo;
                            } else {
                                    // This is an advanced option for reasons of reducing clutter,
                                    // but it is expected to be used by regular users, not devs.
                                    // So we translate the error messages.
                                    networkInterface.setBindTo(oldValue, false);
                                    throw new InvalidConfigValueException(l10n("couldNotChangeBindTo", "failedInterfaces", Arrays.toString(failedAddresses)));
                            }
                    }
            }
    }
    private class darknetAppAllowedHostsCallback extends StringCallback  {
            @Override
            public String get() {
                    return networkInterface.getAllowedHosts();
            }

            @Override
            public void set(String allowedHosts) throws InvalidConfigValueException {
                    if (!allowedHosts.equals(get())) {
                            try {
                            networkInterface.setAllowedHosts(allowedHosts);
                            } catch(IllegalArgumentException e) {
                                    throw new InvalidConfigValueException(e);
                            }
                    }
            }
    }
    
    private class darknetAppPortCallback extends IntCallback  {
        @Override
        public Integer get() {
                return port;
        }

        @Override
        public void set(Integer newPort) throws NodeNeedRestartException {
                if(port != newPort) {
                        throw new NodeNeedRestartException("Port cannot change on the fly");
                }
        }
    }
    private class darknetAppEnabledCallback extends BooleanCallback  {
        @Override
        public Boolean get() {
                synchronized(DarknetAppServer.class) {
                        return myThread != null;
                }
        }
        @Override
        public void set(Boolean val) throws InvalidConfigValueException {
                if (get().equals(val))
                        return;
                synchronized(DarknetAppServer.class) {
                        if(val) {
                                // Start it
                                myThread = new Thread(DarknetAppServer.this, "DarknetAppServer");
                        } else {
                                myThread.interrupt();
                                myThread = null;
                                DarknetAppServer.this.notifyAll();
                                return;
                        }
                }
                myThread.setDaemon(true);
                myThread.start();
        }
    }
    
    private static String l10n(String key, String pattern, String value) {
        return NodeL10n.getBase().getString("DarknetAppServer."+key, pattern, value);
    }
    
    @Override
    public void run() {
        try {
            networkInterface.setSoTimeout(500);
	} catch (SocketException e1) {
            Logger.error(this, "Could not set so-timeout to 500ms; on-the-fly disabling of the interface will not work");
	}
    boolean finishedStartup = false;
    while(true) {
        synchronized(DarknetAppServer.class) {
                while(darknetAppConnections > 100) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                                // Ignore
                        }
                }
                if((!finishedStartup) && this.finishedStartup)
                        finishedStartup = true;
                if(myThread == null) return;
        }
        Socket conn = networkInterface.accept();
        if (WrapperManager.hasShutdownHookBeenTriggered())
                return;
        if(conn == null)
            continue; // timeout
        if(logMINOR)
            Logger.minor(this, "Accepted connection");
        SocketHandler sh = new SocketHandler(conn, finishedStartup);
        sh.start();
        }
    }
    //Copied from SimpleToadletServer
    public class SocketHandler implements PrioRunnable {
        Socket sock;
        final boolean finishedStartup;

        public SocketHandler(Socket conn, boolean finishedStartup) {
                this.sock = conn;
                this.finishedStartup = finishedStartup;
        }

        // A thread starts for each accepted socket connection
        void start() {
            new Thread(this).start();
            synchronized(DarknetAppServer.class) {
                darknetAppConnections++;
            }   
        }
        
        @Override
        public void run() {
            freenet.support.Logger.OSThread.logPID(this);
                if(logMINOR) Logger.minor(this, "Handling connection");
                try {
                    // Handles an incoming request
                    DarknetAppConnectionHandler.handle(sock,DarknetAppServer.this);
                } catch (OutOfMemoryError e) {
                        OOMHandler.handleOOM(e);
                        System.err.println("SimpleToadletServer request above failed.");
                        Logger.error(this, "OOM in SocketHandler");
                } catch (Throwable t) {
                        System.err.println("Caught in SimpleToadletServer: "+t);
                        t.printStackTrace();
                        Logger.error(this, "Caught in SimpleToadletServer: "+t, t);
                } finally {
                    synchronized(DarknetAppServer.class) {
                        darknetAppConnections--;
                        DarknetAppServer.class.notifyAll();
                    }
                }
                if(logMINOR) Logger.minor(this, "Handled connection");
        }

        @Override
        public int getPriority() {
                return NativeThread.HIGH_PRIORITY-1;
        }

    }
    
}
