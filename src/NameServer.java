import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by RandyZhongbin on 4/24/2015.
 */
public class NameServer {
    //set server parameters
    private Selector selector = null;
    private ServerSocketChannel serverSocketChannel = null;
    private ServerSocket serverSocket = null;
    Map<String,String> registerServers = new HashMap<String,String>();
    public NameServer(int portNo){

        if (portNo < 0 || portNo > 65535){
            System.err.println("Invalid command line argument for Name Server");
            System.exit(1);
        }
        try{
            // open selector
            selector = Selector.open();
            // open socket channel
            serverSocketChannel = ServerSocketChannel.open();
            // set the socket associated with this channel
            serverSocket = serverSocketChannel.socket();
            // set Blocking mode to non-blocking
            SelectableChannel selectableChannel = serverSocketChannel.configureBlocking(false);
           try {
               // bind port
               serverSocket.bind(new InetSocketAddress(portNo));
               // registers this channel with the given selector, returning a selection key
               serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
           } catch (BindException e){
               System.err.println("Cannot listen on the given port" + portNo);
           }
            System.out.println("Name Server is activated, listening on port: "+ portNo);

            while(selector.select() > 0){
                for (SelectionKey key : selector.selectedKeys()) {
                    // test whether this key's channel is ready to accept a new socket connection
                    if (key.isAcceptable()) {
                        // accept the connection
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel sc = server.accept();
                        if (sc == null)
                            continue;
                        System.out.println("Connection accepted from: " + sc.getRemoteAddress());
                        // set blocking mode of the channel
                        sc.configureBlocking(false);
                        // allocate buffer
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        // set register status to READ
                        sc.register(selector, SelectionKey.OP_READ, buffer);
                    }
                    // test whether this key's channel is ready for reading from Client
                    else if (key.isReadable()) {
                        // get allocated buffer with size 1024
                        ByteBuffer buffer = (ByteBuffer) key.attachment();
                        SocketChannel sc = (SocketChannel) key.channel();
                        int readBytes = 0;
                        String message = null;
                        // try to read bytes from the channel into the buffer
                        try {
                            int ret;
                            try {
                                while ((ret = sc.read(buffer)) > 0)
                                    readBytes += ret;
                            } catch (Exception e) {
                                readBytes = 0;
                            } finally {
                                buffer.flip();
                            }
                            // finished reading, form message
                            if (readBytes > 0) {
                                message = Charset.forName("UTF-8").decode(buffer).toString();
                                buffer = null;
                            }
                        } finally {
                            if (buffer != null)
                                buffer.clear();
                        }
                        // react by Client's message
                        if (readBytes > 0) {
                            //System.out.println("Message from Client" + sc.getRemoteAddress() + ": " + message);
                            // if exit, close socket channel
                            String reply = null;
                            String[] incomingMsg = message.trim().split(";");
                            if ("R".equalsIgnoreCase(incomingMsg[0])) {
                                //System.out.println("Client " + sc.getRemoteAddress() +" finish up");
                                String serverName = incomingMsg[1];
                                String port = incomingMsg[2];
                                String ipAddr = incomingMsg[3];
                                //save server info
                                try{
                                    registerServers.put(serverName, port + " ; " + ipAddr);
                                    reply = "Registration is successful";
                                } catch (Exception e){
                                    //out.println("error");
                                    System.err.println("Error occurred when register with name server.");
                                    message = "Error.";
                                }
                                //sc.close();
                            } else if("L".equalsIgnoreCase(incomingMsg[0])){
                                String serverName = incomingMsg[1];
                                if(registerServers.containsKey(serverName)){
                                    reply = registerServers.get(serverName);
                                } else {
                                    reply = serverName + " is not registered with name server";
                                }
                            } else {
                                reply = "Error incoming message format";
                            }
                                buffer = null;
                                // set register status to WRITE
                                sc.register(key.selector(), SelectionKey.OP_WRITE, reply);
                        }
                    }
                    else if (key.isWritable()) {
                        //System.err.println("now the key is writable and ready to send to client");
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        buffer.put(((String) key.attachment()).getBytes());
                        buffer.flip();
                        sc.write(buffer);
                        System.err.println("now the key is send to client");
                        // set register status to READ
                        sc.register(key.selector(), SelectionKey.OP_READ, buffer);
                    }
                }
                if (selector.isOpen()) {
                    selector.selectedKeys().clear();
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (serverSocketChannel != null) {
                try {
                    serverSocketChannel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
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
