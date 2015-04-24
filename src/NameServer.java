/**
 * Created by sam on 3/04/2015.
 */

import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NameServer {

    public class MapModel{

        public String hostname;
        public String ip;
        public Integer port;

        public MapModel() {

        }

        public MapModel(String hostname, String ip, Integer port){
            this.hostname = hostname;
            this.ip = ip;
            this.port = port;
        }

        public String toString(){
            return this.ip + ":" + this.port.toString();
        }
    }

    private List<MapModel> hosts = new ArrayList<>();

    public NameServer () {
    }

    public void Register(String line) {


        // register:hostname:ip:port -> hostname:ip:port
        // lookup:hostname -> hostname:ip:port

        MapModel model = new MapModel();

    }

    public MapModel Lookup(String host) {
        return hosts.stream().filter( x -> x.hostname.equals(host)).findFirst().orElse(null);
    }

    public static void main(String[] args)
    {
        if(args.length != 1) {
            System.err.println("Invalid command line arguments for NameServer");
            System.exit(-1);
        }

        String portString = args[0];
        int port = 0;

        try {
            port = Integer.parseInt(portString);
        } catch(Exception e){
            System.err.println("Invalid command line arguments for NameServer");
            System.exit(-1);
        }

        if(port < 0 || port > 65535){
            System.err.print("Cannot listen on given port number ");
            System.err.println(portString);
            System.exit(-1);
        }

        System.err.println("Name Server waiting for incoming connections...");

        NameServer server = new NameServer();

        try {
            server.listen(port);
        }catch (IOException e){
            System.err.print("Cannot listen on given port number ");
            System.err.println(portString);
            System.exit(-1);
        }
    }

    public void listen(int listenPort) throws java.io.IOException {

        ServerSocket serverSocket = null;

        try {

            serverSocket = new ServerSocket(listenPort);

            System.out.println("<TCPServer> Server is activated, listening on port: " + listenPort);

        } catch (IOException e) {
            e.printStackTrace();
        }

        Socket connSocket = null;

        while(true) {

            try {
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

            String line = in.readLine();

            try {
                // Register format: register:hostname:ip:port -> hostname:ip:port
                // Lookup format: lookup:hostname -> hostname:ip:port

                Matcher m = Pattern.compile("(^lookup|register)[:]([a-zA-Z0-9.]*)[:]?([0-9.]*)[:]?([0-9]*)").matcher(line.trim());

                if(m.matches()) {

                    String command = m.group(1);
                    String hostname = m.group(2);
                    String ip = m.group(3);
                    String port = m.group(4);

                    if(command.equals("lookup")){

                        MapModel model = this.Lookup(hostname);

                        if(model != null) {
                            out.println(model.toString());
                        }else{
                            out.println("Error: Process has not registered with the Name Server");
                        }
                    }

                    if(command.equals("register")){
                        MapModel model = new MapModel(hostname, ip, Integer.parseInt(port));
                        hosts.add(model);
                        out.println(model.toString());
                    }

                }

            }catch(Exception e){
                out.println("Error: Process has not registered with the Name Server");
            }

            out.close();
            in.close();
            connSocket.close();
        }
    }
}
