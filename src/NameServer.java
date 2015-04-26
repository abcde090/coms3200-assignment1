import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

interface IRPCServer {
    String Respond(String message);
}

public class NameServer implements IRPCServer {

    private HashMap<String, InetSocketAddress> hosts = new HashMap<String, InetSocketAddress>();

    public NameServer() {
    }

    private InetSocketAddress Lookup(String host) {
        return hosts.getOrDefault(host, null);
    }

    private InetSocketAddress Register(String host, String ip, String port) {
        hosts.put(host, new InetSocketAddress(ip, Integer.parseInt(port)));
        return hosts.get(host);
    }

    @Override
    public String Respond(String message) {

        String regex = "(^lookup|register)[:]([a-zA-Z0-9.]*)[:]?([0-9.]*)[:]?([0-9]*)";

        Matcher pattern = Pattern.compile(regex).matcher(message.trim());

        if (!pattern.matches()) {
            return "Error: Process has not registered with the Name Server\n";
        }

        String command = pattern.group(1); // required
        String hostname = pattern.group(2); // required

        String ip = pattern.group(3); // optional
        String port = pattern.group(4); // optional

        InetSocketAddress address = null;

        if (command.equals("lookup")) {
            address = this.Lookup(hostname);
        }

        if (command.equals("register")) {
            address = this.Register(hostname, ip, port);
        }

        if (address == null) {
            return "Error: Process has not registered with the Name Server\n";
        }

        return hostname + ":" + address.getHostString() + ":" + address.getPort() + "\n";
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Invalid command line arguments for NameServer");
            System.exit(-1);
        }

        String portString = args[0];

        int port = Integer.parseInt(portString);

        if (port < 0 || port > 65535) {
            System.err.print("Cannot listen on given port number ");
            System.err.println(portString);
            System.exit(-1);
        }

        System.err.println("Name Server waiting for incoming connections...");

        try {

            Util.RpcServer(new InetSocketAddress(port), new NameServer());

        } catch (IOException e) {
            System.err.print("Cannot listen on given port number ");
            System.err.println(portString);
            System.exit(-1);
        }
    }

    public static class XDRDecoder {

        private String message;
        private int length;

        public XDRDecoder(ByteBuffer buffer) throws UnsupportedEncodingException {

            buffer.position(0);

            this.length = Math.min(buffer.getInt(), buffer.limit() - 4);

            byte[] byteString = new byte[this.length];

            buffer.get(byteString, 0, this.length);

            this.message = new String(byteString, "UTF-8");
        }

        public XDRDecoder(InputStream stream) throws IOException {

            byte[] messageLengthBuffer = new byte[4];

            stream.read(messageLengthBuffer, 0, 4);

            ByteBuffer buffer = ByteBuffer.wrap(messageLengthBuffer);

            this.length = buffer.getInt(0);

            byte[] messageBuffer = new byte[length];

            stream.read(messageBuffer, 0, length);

            this.message = new String(messageBuffer, "UTF-8");
        }

        public String GetMessage() {
            return this.message;
        }

        public boolean isValid() {
            return this.length != 0 && this.message.length() == this.length;
        }

    }

    public static class XDREncoder {

        private String message;
        private ByteBuffer buffer;

        public XDREncoder(String message) {

            this.message = message;

            this.buffer = ByteBuffer.allocate(1024);

            this.buffer.putInt(this.message.length());

            this.buffer.put(this.message.getBytes());

            this.buffer.flip();
        }

        public ByteBuffer GetByteBuffer() {
            return buffer;
        }

        public byte[] GetBytes() {

            byte[] byteArray = new byte[buffer.limit()];

            buffer.get(byteArray, 0, buffer.limit());

            return byteArray;
        }

    }

    public static class Util {

        public static InetSocketAddress LookUpHost(int nameServerPort, String hostname) {

            String reply;

            try {
                reply = Util.RpcCall(new InetSocketAddress("127.0.0.1", nameServerPort), "lookup:" + hostname);
            } catch (IOException e) {
                return null;
            }

            String regex = "(^" + hostname + ")[:]([0-9.]*)[:]([0-9]*)";

            Matcher pattern = Pattern.compile(regex).matcher(reply.trim());

            if (!pattern.matches()) {
                return null;
            }

            String ip = pattern.group(2);
            int port = Integer.parseInt(pattern.group(3));

            return new InetSocketAddress(ip, port);
        }

        public static boolean RegisterHost(int nameServerPort, String hostname, String ip, String port) {

            String reply;

            try {
                reply = Util.RpcCall(new InetSocketAddress("127.0.0.1", nameServerPort), "register:" + hostname + ":" + ip + ":" + port);
            } catch (IOException e) {
                return false;
            }

            return reply.trim().equals(hostname + ":" + ip + ":" + port);
        }

        public static String RpcCallContinuous(Socket clientSocket, String request) throws IOException {

            XDREncoder writer = new XDREncoder(request);

            clientSocket.getOutputStream().write(writer.GetBytes());

            XDRDecoder reader = new XDRDecoder(clientSocket.getInputStream()); // block

            return reader.GetMessage();
        }

        public static String RpcCall(InetSocketAddress address, String request) throws IOException {

            XDREncoder writer = new XDREncoder(request);

            // Connect to the process listening on address and port
            Socket clientSocket = new Socket(address.getHostString(), address.getPort());

            clientSocket.getOutputStream().write(writer.GetBytes());

            XDRDecoder reader = new XDRDecoder(clientSocket.getInputStream()); // block

            clientSocket.close();

            return reader.GetMessage();
        }

        public static void RpcServer(InetSocketAddress bindAddress, IRPCServer RPCServer) throws IOException {

            Selector selector;
            ServerSocketChannel serverSocketChannel;
            ServerSocket serverSocket;

            // open selector
            selector = Selector.open();
            // open socket channel
            serverSocketChannel = ServerSocketChannel.open();
            // set the socket associated with this channel
            serverSocket = serverSocketChannel.socket();
            // set Blocking mode to non-blocking
            serverSocketChannel.configureBlocking(false);
            // bind port
            serverSocket.bind(bindAddress);
            // registers this channel with the given selector, returning a selection key
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (selector.select() > 0) {

                Set<SelectionKey> selectedKeys = selector.selectedKeys();

                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {

                    SelectionKey key = keyIterator.next();

                    // test whether this key's channel is ready to accept a new socket connection
                    if (key.isAcceptable()) {
                        // accept the connection
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel sc = server.accept();
                        if (sc == null)
                            continue;
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

                        String message;

                        int ret;
                        int bytesRead = 0;

                        // try to read bytes from the channel into the buffer
                        try {

                            while ((ret = sc.read(buffer)) > 0) {
                                bytesRead += ret;
                            }

                        } catch (IOException ignored) {
                            key.cancel();
                            sc.close();
                            keyIterator.remove();
                            continue;
                        }

                        if (ret == -1) {
                            key.cancel();
                            sc.close();
                            keyIterator.remove();
                            continue;
                        }

                        // NOTE We assume messages are buffered by the operating system and
                        // ar not received in chunks but in one single chunk

                        XDRDecoder decoder = new XDRDecoder(buffer);

                        if (decoder.isValid()) {

                            message = decoder.GetMessage();

                            String reply = RPCServer.Respond(message);

                            // set register status to WRITE
                            sc.register(key.selector(), SelectionKey.OP_WRITE, reply);
                        }

                    }

                    // test whether this key's channel is ready for sending to Client
                    else if (key.isWritable()) {

                        SocketChannel sc = (SocketChannel) key.channel();

                        XDREncoder writer = new XDREncoder((String) key.attachment());

                        ByteBuffer buffer = writer.GetByteBuffer();

                        try {
                            sc.write(buffer);

                            buffer.clear();

                            // set register status to READ
                            sc.register(key.selector(), SelectionKey.OP_READ, buffer);

                        } catch (IOException ignored) {
                            sc.close();
                            key.cancel();
                        }

                    }

                    keyIterator.remove();
                }

                if (selector.isOpen()) {
                    selector.selectedKeys().clear();
                } else {
                    break;
                }
            }

            try {
                serverSocketChannel.close();
            } catch (IOException ignored) {
            }

        }
    }
}
