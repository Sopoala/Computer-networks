import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;


public class NameServer {
	ServerSocket serverSocket = null;
	PrintWriter out = null;
	BufferedReader in = null;
	Map<String,String> registerServers = new HashMap<String,String>();
	
	public NameServer(int portNo) throws IOException, NumberFormatException{
		if (portNo < 0 || portNo > 65533){
			System.err.println("Invalid command line argument for Name Server");
			System.exit(1);
		}
		
		try{
			// listen on the portNo
			serverSocket = new ServerSocket(portNo);
		} catch(BindException e){
			System.err.println("Cannot listen on given port number "+portNo);
        	System.exit(1);
		} catch (IOException e) {
        	e.printStackTrace();
		}
		
		Socket connSocket = null;
		while(true){
			try{
				System.out.println("Name Server waiting for incoming connections ...");
				// block, waiting for a connection request
				connSocket = serverSocket.accept();
				// At this point we already have had a connection
				System.out.println("Connection accepted from: " + connSocket.getInetAddress().getHostName());
			} catch (IOException e) {
		    	e.printStackTrace();
		    }
			
			// Now we have a socket to use for the communication
			// Create a printwriter and bufferedreader for interaction
			out = new PrintWriter(connSocket.getOutputStream(), true);
		    in = new BufferedReader(new InputStreamReader(connSocket.getInputStream()));
		    String line;
		 // Read a line from the stream - until the stream closes
		    while ((line=in.readLine()) != null) {
		    	String[] incomingMsg = line.split(";");
				//check whether the query is a lookup or register (starting letter 'L' represent lookup 'R' represent register)
		    	if(incomingMsg[0].equals("R"))
		    	{
		    		String serverName = incomingMsg[1];
		    		String port = incomingMsg[2];
		    		String ipAddr = incomingMsg[3];
		    		//save server info 
		    		registerServers.put(serverName, port +" ; "+ipAddr);
		    	}
		    	else if(incomingMsg[0].equals("L"))
		    	{
		    		String serverName = incomingMsg[1];
		    		if(registerServers.containsKey(serverName))
		    		{
		    			//send IP address and port number back to client
		    			out.println(registerServers.get(serverName));
		    		}
		    		//not found
		    		else
		    		{
		    			out.println("error");
		    		}
		    	}
		    	//invalid message
		    	else
		    	{
		    		System.err.println("Message format is wrong, Please use 'R' or 'L' as starting letter and sepereate each pattern using ';'!!!");
		    	}
		    }
		    out.close();
			in.close();
			connSocket.close();
		}
	}
	
	public static void main(String[] args)  throws IOException {
		System.out.println("Please specify a port no for Name Server to listen:");
		BufferedReader stdin = new BufferedReader(
	                new InputStreamReader(System.in));
		String userInput = stdin.readLine();
		try{
			int portNumber = Integer.parseInt(userInput);
			new NameServer(portNumber);
		}
		catch(NumberFormatException e){
			System.err.println("Invalid command line arguments");
			System.exit(1);
		}
		
	}
}
