import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import org.omg.CORBA.PUBLIC_MEMBER;


public class Store {
	private ServerSocket serverSocket = null;
    private PrintWriter out = null;
    private BufferedReader in = null;
    private Socket connSocket = null;
    private int nameServerPort;
    private int storePort; 
    private String ipAddr = "127.0.0.1";
    private Map<String,String> items = new HashMap<String,String>();
    
	public Store(int storePort, String fileName, int nameServerPort) throws IOException, NumberFormatException{
		if (storePort < 0 || storePort > 65533 || nameServerPort < 0 || nameServerPort > 65533){
			System.err.println("Invalid command line argument for Name Server");
			System.exit(1);
		}
		// try to connect to the name server
		conncetToServer("Name", ipAddr, nameServerPort);
		
		// register with name server
		try{
			out.println("R;Store;"+storePort+";"+ipAddr);
		} catch(Exception e){
			System.err.println("Registration with NameServer failed\n");
			System.exit(1);
		}
		
		// send look-up request for bank server to get the ip address and port
		String bankAddr = lookUpServer("Bank");
		
		// send look-up request for content server to get the ip address and port
		String contentAddr = lookUpServer("Content");		
		
		closeConn(connSocket,out, in);
		
		// read contents from txt file and store the contents to a hash map items
		readFile(fileName);
		
		// connect to bank server
		conncetToServer("Bank", bankAddr.split(";")[1], Integer.parseInt(bankAddr.split(";")[0]));
		
		// connect to content server
		conncetToServer("Content", contentAddr.split(";")[1], Integer.parseInt(contentAddr.split(";")[0]));
		// listening for incoming connections
		try {
            serverSocket = new ServerSocket(storePort);
        }
        catch (BindException e){
        	System.err.println("Cannot listen on given port number "+storePort);
        	System.exit(1);
        }
        catch (IOException e) {
        	e.printStackTrace();
		}
		
		while(true) {
        	try {
        		System.out.println("Store Server waiting for incoming connections ...");
				// block, waiting for a conn. request
				connSocket = serverSocket.accept();
				// At this point, we have a connection
				System.out.println("Connection accepted from: " + connSocket.getInetAddress().getHostName());
		    } catch (IOException e) {
		    	e.printStackTrace();
		    }
		    // Now have a socket to use for communication
		    // Create a PrintWriter and BufferedReader for interaction with our stream "true" means we flush the stream on newline
        	PrintWriter out = new PrintWriter(connSocket.getOutputStream(), true);
        	BufferedReader in = new BufferedReader(new InputStreamReader(connSocket.getInputStream()));
		    // Read a line from the stream - receive the requests
		    String line = in.readLine();
		    // split the request
	    	String[] inRequest =  line.split(";");
	    	if(inRequest[0].equals("P")){
	    		int itemID = Integer.parseInt(inRequest[1]);
	    		String result = processPriceCheck(itemID);
	    		out.println(result);
	    	} else if(inRequest[0].equals("B")){
	    		int itemID = Integer.parseInt(inRequest[1]);
	    		String creditCard = inRequest[2];
	    		out.println(processBuying(itemID));
	    	} else {
	    		System.err.println("Invalid command line argument");
	    		System.exit(1);
	    	}
	    	String request = inRequest[0];
	    	String requestDetail = inRequest[1];
	    	//do a search based on request and keyword
	    	String result = processRequest(request,requestDetail);
	    	out.println(result);
		    
		    //System.err.println("Store Server connection closed");
		    //closeConn(connSocket, out, in);
		}
	
	}

	private String processPriceCheck(int itemID) {
		// TODO Auto-generated method stub
		return null;
	}

	private String processBuying(int itemID) {
		// TODO Auto-generated method stub
		return null;
	}

	private String processRequest(String request, String keyWord) {
		// TODO Auto-generated method stub
		return null;
	}

	private void closeConn(Socket connSocket, PrintWriter out, BufferedReader in)
			throws IOException {
		if(connSocket!=null&&out!=null&&in!=null)
		{
			out.close();
			in.close();
			connSocket.close();
		}
	}

	private void conncetToServer(String name, String ip, int port){
		try {
			connSocket = new Socket(ip, port);
			out = new PrintWriter(connSocket.getOutputStream(),true);
			in = new BufferedReader(
				    new InputStreamReader(connSocket.getInputStream()));
		}catch (ConnectException e){
        	System.err.println("Unable to connect to " + name + " server located at port "+ port);
        	System.exit(1);
        }
		catch (Exception e) {
            e.printStackTrace();
        }
	}

	private void readFile(String fileName) {
		BufferedReader br = null;
		 
		try {
			String sCurrentLine;
			br = new BufferedReader(new FileReader(fileName));
 
			while ((sCurrentLine = br.readLine()) != null) {
				String[] item = sCurrentLine.split(" ");
				items.put(item[0], item[1]);
			}
 
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		
	}


	private String lookUpServer(String name) {
		String reply = null;
		out.println("L" + name);
		try {
			reply = in.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(reply.equals("error")){
			System.err.println(name + "has not registered\n");
			System.exit(1);
			return null;
		} else {			
			return reply;
		}
				
	}


	public static void main(String[] args) throws IOException, NumberFormatException{
		System.out.println("Please specify store server port number, stock file name and name server port number\nIN THE FORMAT\n'Store Server Port number|Stock-file name|Name Server port number':");
		BufferedReader stdin = new BufferedReader(
	                new InputStreamReader(System.in));
		String userInput = stdin.readLine();
		String input[] = userInput.split("|");
		try{
			int storePort = Integer.parseInt(input[0]);
			String stockfile = input[1];
			int nameServerPort = Integer.parseInt(input[2]);
			new Store(storePort, stockfile, nameServerPort);
		} catch(NumberFormatException e){
			System.err.println("Invalid command line arguments");
			System.exit(1);
		} catch(FileNotFoundException e){
			System.err.println("File Not Found!");
			System.exit(1);
		}
	}

}
