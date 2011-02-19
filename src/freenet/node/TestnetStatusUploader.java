/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;

import freenet.support.Logger;

/**
 * Testnet StatusUploader.
 * This is a simple client for uploading status of the client to the
 * auditing server, which parses information and adds it to the database 
 * 
 * NOTE THAT IF THIS IS ENABLED, YOU HAVE NO ANONYMITY! It may also be possible
 * to exploit this for denial of service, as it is not authenticated in any way.
 * 
 * 
 */
public class TestnetStatusUploader implements Runnable {

	public TestnetStatusUploader(Node node2, int updateInterval) {
		this.node = node2;
		this.updateInterval = updateInterval;
		Logger.error(this, "STARTING TESTNET STATUSUPLOADER!");
		Logger.error(this, "ANONYMITY MODE: OFF");
		System.err.println("STARTING TESTNET STATUSUPLOADER!");
		System.err.println("ANONYMITY MODE: OFF");
		System.err.println("You have no anonymity. Thank you for running a testnet node, this will help the developers to efficiently debug Freenet, by letting them (and anyone else who knows how!!) automatically fetch your log files.");
		System.err.println("We repeat: YOU HAVE NO ANONYMITY WHATSOEVER. DO NOT POST ANYTHING YOU DO NOT WANT TO BE ASSOCIATED WITH.");
		System.err.println("If you want a real Freenet node, with anonymity, turn off testnet mode.");
	}

	void start() {
		node.executor.execute(this, "TestnetStatusUploader thread");
	}
	
	private final Node node;
	private final int updateInterval;
	private Socket client;
	
	static final String serverAddress = "amphibian.dyndns.org";
	static final int serverPort = TestnetController.PORT;
	
	public void run() {
		    freenet.support.Logger.OSThread.logPID(this);
			//thread loop
			
			while(true){
			
				// Set up client socket
				try
				{
					client = new Socket(serverAddress, serverPort);
					PrintStream output = new PrintStream(client.getOutputStream());
	            		
					output.println(node.exportDarknetPublicFieldSet().toString());
					output.println();
					output.println(node.getFreevizOutput());
					output.close();
					
					client.close();
					
				} catch (IOException e){
					Logger.error(this, "Could not open connection to the uploadhost");
					System.err.println("Could not open connection to the uploadhost");
				}
				
				try{
					Thread.sleep(updateInterval);
						
				//how i love java 
				}catch (InterruptedException e){
					return;
					
				}
				
			}
			

	}

	public static long makeID() {
		boolean sleep = false;
		long sleepTime = 1000;
		long maxSleepTime = 60 * 60 * 1000;
		while(true) {
			if(sleep) {
				try {
					Thread.sleep(sleepTime);
					sleepTime *= 2;
					if(sleepTime > maxSleepTime)
						sleepTime = maxSleepTime;
				} catch (InterruptedException e1) {
					// Ignore
				}
			}
			System.err.println("Trying to contact testnet coordinator to get an ID");
			sleep = true;
			Socket client = null;
			try {
				client = new Socket(serverAddress, serverPort);
				InputStream is = client.getInputStream();
				OutputStream os = client.getOutputStream();
				InputStreamReader isr = new InputStreamReader(new BufferedInputStream(is), "UTF-8");
				BufferedReader br = new BufferedReader(isr);
				OutputStreamWriter osw = new OutputStreamWriter(new BufferedOutputStream(os));
				osw.write("GENERATE\n");
				osw.flush();
				String returned = br.readLine();
				if(returned == null)
					throw new EOFException();
				final String chop = "GENERATEDID:";
				if(!returned.startsWith(chop)) {
					throw new IOException("Bogus return: \""+returned+"\" - expected GENERATEDID:");
				}
				returned = returned.substring(chop.length());
				try {
					return Long.parseLong(returned);
				} catch (NumberFormatException e) {
					throw new IOException("Bogus return wasn't a number: \""+returned+"\"");
				}
			} catch (UnknownHostException e) {
				System.err.println("Unknown host, waiting...");
				continue;
			} catch (IOException e) {
				System.err.println("Unable to connect: "+e+" , waiting...");
				continue;
			} finally {
				try {
					if(client != null)
						client.close();
				} catch (IOException e) {
					// Ignore
				}
			}
			
		}
	}
	
	
}