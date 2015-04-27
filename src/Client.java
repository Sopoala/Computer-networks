import java.io.*;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

/**
 * Created by RandyZhongbin on 4/25/2015.
 */
public class Client {
    private Selector selector = null;
    private ServerSocketChannel serverSocketChannel = null;
    private ServerSocket serverSocket = null;
    private Socket connSocket = null;
    private String ipAddr = "127.0.0.1";
    private PrintWriter out = null;
    private BufferedReader in = null;
    int storePort = 0;
    boolean storeKnown = false;
    public Client(int request, int nameServerPort){
        // if the store server is unknown, send look up request message
        if(!storeKnown){
            // Look up for Bank server
            String storeAddr = contactServer("L;Store", ipAddr, nameServerPort);
            storePort = Integer.parseInt(storeAddr.split(";")[0].trim());
            storeKnown = true;
        }
        // connect to store server
        String result = contactServer(String.valueOf(request),ipAddr,storePort);
        System.out.println(result);

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
            channel.register(selector, SelectionKey.OP_CONNECT);
            while (selector.select() > 0) {
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
    public static void main(String[] args) throws IOException, NumberFormatException{
        System.out.println("Please enter your request and the name server port");
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        String userInput;
        while((userInput = stdin.readLine()) != null){
            String input[] = userInput.split(" ");
            try{
                int request = Integer.parseInt(input[0]);
                int nameServerPort = Integer.parseInt(input[1]);
                new Client(request, nameServerPort);
//                userInput = stdin.readLine();
                System.out.println("\nPlease enter your request and the name server port");
            } catch(NumberFormatException e){
                System.err.println("Invalid command line arguments\n");
                System.exit(1);
            }
        }

    }
}
