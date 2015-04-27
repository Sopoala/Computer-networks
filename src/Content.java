import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by RandyZhongbin on 4/25/2015.
 */
public class Content {
    private Selector selector = null;
    private ServerSocketChannel serverSocketChannel = null;
    private ServerSocket serverSocket = null;
    private Socket connSocket = null;
    private String ipAddr = "127.0.0.1";
    private PrintWriter out = null;
    private BufferedReader in = null;
    private String itemID = null;
    private Map<String,String> contents = new HashMap<String,String>();
    public Content(int contentPort, String fileName, int nameServerPort)throws IOException, NumberFormatException{
        if (contentPort < 0 || contentPort > 65533 || nameServerPort < 0 || nameServerPort > 65533){
            System.err.println("Invalid command line arguments for Content Server");
            System.exit(1);
        }
        // Prepare registration message
        String request = "R;Content;" + contentPort +";"+ipAddr;
        // Register with name server
        contactServer(request,ipAddr,nameServerPort);
        // read from file
        readFile(fileName);
        // listening for incoming connections
        listenForConnection(contentPort);
    }
    private void listenForConnection(int contentPort) {
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
                serverSocket.bind(new InetSocketAddress(contentPort));
                // registers this channel with the given selector, returning a selection key
                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
                System.out.println("Content server is waiting for incoming connections, listening on port: " + contentPort);
            }catch (BindException e){
                System.err.println("Cannot listen on the given port" + contentPort);
            }
            while (selector.select() > 0) {
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
                                itemID = Charset.forName("UTF-8").decode(buffer).toString();
                                //itemID = Integer.parseInt(message);
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
                                String result = null;
                                String success = "The transaction is successful!\nThe item you purchased is: ";
                                try{result = success + contents.get(itemID);}
                                catch (Exception e){
                                    System.err.println("Error");
                                    result = "Error";
                                }
                                sc.register(key.selector(), SelectionKey.OP_WRITE, result);

                        }
                    }
                    // test whether this key's channel is ready for sending to Client
                    else if (key.isWritable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        buffer.put(((String) key.attachment()).getBytes());
                        buffer.flip();
                        sc.write(buffer);
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
            System.err.println("Unable to listen on the given port " + contentPort);
            System.exit(1);
        }
        finally {
            if (serverSocketChannel != null) {
                try {
                    serverSocketChannel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private void readFile(String fileName) {
        BufferedReader br = null;

        try {
            String sCurrentLine;
            br = new BufferedReader(new FileReader(fileName));

            while ((sCurrentLine = br.readLine()) != null) {
                String[] item = sCurrentLine.split(" ");
                contents.put(item[0], item[1]);
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
    private String contactServer(String msg, String ipAddr, int serverPort) {
        SocketChannel channel = null;
        String reply = null;
        try{
            channel = SocketChannel.open();
            // set Blocking mode to non-blocking
            channel.configureBlocking(false);
            // set Server info
            InetSocketAddress target = new InetSocketAddress(ipAddr, serverPort);
            // open selector
            Selector selector = Selector.open();
            // connect to Server
            channel.connect(target);
            // registers this channel with the given selector, returning a selection key
            channel.register(selector, SelectionKey.OP_CONNECT);while (selector.select() > 0) {
                for (SelectionKey key : selector.selectedKeys()) {
                    // test connectivity
                    if (key.isConnectable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        // set register status to WRITE
                        sc.register(selector, SelectionKey.OP_WRITE);
                        sc.finishConnect();
                    }
                    // test whether this key's channel is ready for reading from Server
                    else if (key.isReadable()) {
                        // allocate a byte buffer with size 1024
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        SocketChannel sc = (SocketChannel) key.channel();
                        int readBytes = 0;
                        // try to read bytes from the channel into the buffer
                        try {
                            int ret = 0;
                            try {
                                while ((ret = sc.read(buffer)) > 0)
                                    readBytes += ret;
                            } finally {
                                buffer.flip();
                            }
                            // finished reading, print to Client
                            if (readBytes > 0) {
                                reply = Charset.forName("UTF-8").decode(buffer).toString();
                                //System.out.println(Charset.forName("UTF-8").decode(buffer).toString());
                                buffer = null;
                                selector.close();
                                break;
                            }
                        } finally {
                            if (buffer != null)
                                buffer.clear();
                        }
                        // set register status to WRITE
                        sc.register(selector, SelectionKey.OP_WRITE);
                    }
                    // test whether this key's channel is ready for writing to Server
                    else if (key.isWritable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        // send to Server
                        channel.write(Charset.forName("UTF-8").encode(msg));
                        // set register status to READ
                        sc.register(selector, SelectionKey.OP_READ);
                    }
                }
                if (selector.isOpen()) {
                    selector.selectedKeys().clear();
                } else {
                    break;
                }
            }
            return reply;
        } catch (IOException e) {
            e.printStackTrace();
            return "Error";
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    public static void main(String[] args) throws IOException, NumberFormatException {
        System.out.println("Please specify content server port number, stock file name and name server port number\nIN THE FORMAT\nContent Server Port number (SPACE) Stock-file name (SPACE) Name Server port number:");
        BufferedReader stdin = new BufferedReader(
                new InputStreamReader(System.in));
        String userInput = stdin.readLine();
        String input[] = userInput.split(" ");
        try {
            int contentPort = Integer.parseInt(input[0]);
            String contentFile = input[1];
            int nameServerPort = Integer.parseInt(input[2]);
            new Content(contentPort, contentFile, nameServerPort);
        } catch (NumberFormatException e) {
            System.err.println("Invalid command line arguments");
            System.exit(1);
        } catch (FileNotFoundException e) {
            System.err.println("File Not Found!");
            System.exit(1);
        }
    }

}
