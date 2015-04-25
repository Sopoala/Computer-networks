import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.*;


public class Store {
    private ServerSocket serverSocket = null;
    private PrintWriter out = null;
    private BufferedReader in = null;
    private Socket connSocket = null;
    private int nameServerPort;
    private int storePort, bankPort, contentPort;
    private String ipAddr = "127.0.0.1";
    private Map<String, String> stocks = new LinkedHashMap<String, String>();
    private ArrayList<String> stocksAL = new ArrayList<>();
    private Selector selector = null;
    private SocketChannel channelBank = null;
    private SocketChannel channelContent = null;
    private String storeMsgSend = null;

    public Store(int storePort, String fileName, int nameServerPort) throws IOException, NumberFormatException {
        if (storePort < 0 || storePort > 65533 || nameServerPort < 0 || nameServerPort > 65533) {
            System.err.println("Invalid command line argument for Name Server");
            System.exit(1);
        }
        // try to connect to the name server
        connectToServer(ipAddr, nameServerPort);

        // register with name server
        try {
            out.println("R;Store;" + storePort + ";" + ipAddr);
        } catch (Exception e) {
            System.err.println("Registration with NameServer failed\n");
            System.exit(1);
        }

        // send look-up request for bank server to get the ip address and port
        String bankAddr = lookUpServer("Bank");
        bankPort = Integer.parseInt(bankAddr.split(";")[0]);
        // send look-up request for content server to get the ip address and port
        String contentAddr = lookUpServer("Content");
        contentPort = Integer.parseInt(bankAddr.split(";")[0]);

        // close the connection to name server
        closeConn(connSocket, out, in);

        // read contents from txt file and store the contents to a hash map
        readFile(fileName);

    }


    public void startListening(int listeningPort) {

        Selector selector = null;
        ServerSocketChannel serverSocketChannel = null;
        ServerSocket socket = null;
        try {
            // opening a selector
            selector = Selector.open();
            // opening a channel
            serverSocketChannel = ServerSocketChannel.open();

            // saving the socket associated with the channel
            socket = serverSocketChannel.socket();
            // setting the blocking type to false so that it doesnt crash when
            // multiple clients connect
            serverSocketChannel.configureBlocking(false);

            try {
                // binding the socket with the port at which we want to listen
                socket.bind(new InetSocketAddress(storePort));
                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            } catch (Exception e) {
                System.err.print("Store server unable to listen on given port\n");
                System.exit(1);
            }
            System.out.println("Store server waiting for incoming connections\n");
            while (selector.select() > 0) {
                for (SelectionKey key : selector.selectedKeys()) {
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                        SocketChannel socketChannel = serverChannel.accept();
                        if (socketChannel == null) {
                            continue;
                        }
                        socketChannel.configureBlocking(false);
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        socketChannel.register(selector, SelectionKey.OP_READ, buffer);
                        // read the buffer for the client request
                    } else if (key.isReadable()) {
                        ByteBuffer buffer = (ByteBuffer) key.attachment();
                        SocketChannel socketChannel = (SocketChannel) key.channel();
                        int readBytes = 0;
                        String message = null;
                        int ret = 0;
                        try {
                            while ((ret = socketChannel.read(buffer)) > 0) {
                                readBytes += ret;
                            }
                        } catch (Exception e) {
                            readBytes = 0;
                        } finally {
                            buffer.flip();
                        }
                        String reply = null;
                        if (readBytes > 0) {
                            message = Charset.forName("UTF-8").decode(buffer)
                                    .toString();
                            // check for the message validity on the server side
                            // and act accordingly
                            if (message.equals("0")) {
//                                int n = 1;
//                                Iterator<Map.Entry<String, String>> i = stocks.entrySet().iterator();
//                                while(i.hasNext()){
//                                    String id = i.next().getKey();
//                                    reply = (String.valueOf(n) + id+ " "+stocks.get(key) + "\n");
//                                    n++;
                                for(int i = 0; i < stocksAL.size(); i++){
                                    reply = stocksAL.get(i) + "\n";
                                }
                            } else {
                                String itemID = stocksAL.get(Integer.parseInt(message)).split(" ")[0];
                                double itemPrice = Double.parseDouble(stocksAL.get(Integer.parseInt(message)).split(" ")[1]);
                                String creditCard = "1234567890123456";
                                storeMsgSend =itemID +" " + itemPrice + creditCard;
                                String bankReply = contactServer(storeMsgSend,ipAddr,bankPort);
                                if(bankReply.equals("1")){
                                    reply = "Transaction aborted\n";
                                } else {
                                    try{
                                        reply = contactServer(itemID,ipAddr,bankPort);
                                    } catch (Exception e){
                                        reply = "Transaction Aborted\n";
                                    }
                                }
                            }
                            // socketChannel.close();
                            buffer = null;
                            // System.out.println(reply);
                            socketChannel.register(selector, SelectionKey.OP_WRITE, reply);
                        }
                    } else if (key.isWritable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        buffer.put(((String) key.attachment()).getBytes());
                        buffer.flip();
                        sc.write(buffer);
                        // set register status to READ
                        sc.close();
                        System.err.print("Store Server connection closed\n");
                        //System.err.print("Store Server waiting for incoming connections\n");
                    }

                }

                if (selector.isOpen()) {
                    selector.selectedKeys().clear();
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private String contactServer(String storeMsgSend, String ipAddr, int serverPort) {
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
                        channel.write(Charset.forName("UTF-8").encode(storeMsgSend));
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


    public void connectToServer(String request, String ipAddr, int port) {
        SocketChannel channel = null;
        String response = null;
        try {
            // open socket channel
            channel = SocketChannel.open();
            // set Blocking mode to non-blocking
            channel.configureBlocking(false);
            // set Server info
            InetSocketAddress target = new InetSocketAddress(ipAddr, port);
            // open the selector
            selector = Selector.open();
            // connect to Server
            channel.connect(target);
            // registers this channel with the given selector, returning a selection key
            channel.register(selector, SelectionKey.OP_CONNECT);
            while (selector.select() > 0) {

                for (SelectionKey key : selector.selectedKeys()) {

                    if (key.isConnectable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        sc.register(selector, SelectionKey.OP_WRITE);
                        sc.finishConnect();

                    } else if (key.isReadable()) {

                    } else if (key.isWritable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        // send to Server
                        channel.write(Charset.forName("UTF-8").encode(request));
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
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Unable to connect to the server" + "\n");
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


    private void closeConn(Socket connSocket, PrintWriter out, BufferedReader in)
            throws IOException {
        if (connSocket != null && out != null && in != null) {
            out.close();
            in.close();
            connSocket.close();
        }
    }

    private void connectToServer(String ip, int port) {
        try {
            connSocket = new Socket(ip, port);
            out = new PrintWriter(connSocket.getOutputStream(), true);
            in = new BufferedReader(
                    new InputStreamReader(connSocket.getInputStream()));
        } catch (ConnectException e) {
            System.err.println("Unable to connect to name server located at port " + port);
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readFile(String fileName) {
        BufferedReader br = null;

        try {
            String sCurrentLine;
            br = new BufferedReader(new FileReader(fileName));
            int n = 0;
            while ((sCurrentLine = br.readLine()) != null) {
                String[] item = sCurrentLine.split(" ");
                stocks.put(item[0], item[1]);
                stocksAL.add(n, sCurrentLine);
                n++;
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
        out.println("L;" + name);
        try {
            reply = in.readLine();
            System.out.println(reply);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (reply.equals("error")) {
            System.err.println(name + " has not registered\n");
            System.exit(1);
            return null;
        } else {
            return reply;
        }

    }


    public static void main(String[] args) throws IOException, NumberFormatException {
        System.out.println("Please specify store server port number, stock file name and name server port number\nIN THE FORMAT\n'Store Server Port number|Stock-file name|Name Server port number':");
        BufferedReader stdin = new BufferedReader(
                new InputStreamReader(System.in));
        String userInput = stdin.readLine();
        String input[] = userInput.split(" ");
        try {
            int storePort = Integer.parseInt(input[0]);
            String stockfile = input[1];
            int nameServerPort = Integer.parseInt(input[2]);
            new Store(storePort, stockfile, nameServerPort);
        } catch (NumberFormatException e) {
            System.err.println("Invalid command line arguments");
            System.exit(1);
        } catch (FileNotFoundException e) {
            System.err.println("File Not Found!");
            System.exit(1);
        }
    }

}
