/**
 * Created by sam on 3/04/2015.
 */

import java.net.*;
        import java.io.*;
        import java.util.ArrayList;
        import java.util.List;
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;

public class Bank {

    public Bank () {
    }

    public static boolean RegisterWithNameServer(int nameServerPort, int bankPort) {


        try {

            Socket clientSocket = new Socket("127.0.0.1", nameServerPort);

            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true); // "true" means flush at end of line
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            out.println("register:bank:127.0.0.1:" + bankPort);
            // register
            // reply
            String reply = in.readLine(); // blocking

            // Close everything
            out.close();
            in.close();
            clientSocket.close();

            return reply.equals("bank:127.0.0.1:" + bankPort);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void main(String[] args)
    {
        if(args.length != 2) {
            System.err.println("Invalid command line arguments for Bank");
            System.exit(-1);
        }

        String bankPortString = args[0];
        String nameServerPortString = args[1];

        int bankPort = 0;
        int nameServerPort = 0;

        try {
            bankPort = Integer.parseInt(bankPortString);
            nameServerPort = Integer.parseInt(nameServerPortString);
        } catch(Exception e){
            System.err.println("Invalid command line arguments for Bank");
            System.exit(-1);
        }

        if(bankPort < 0 || bankPort > 65535 || nameServerPort < 0 || nameServerPort > 65535){
            System.err.println("Bank unable to listen on given port");
            System.exit(-1);
        }

        if(RegisterWithNameServer(nameServerPort, bankPort) == false) {
            System.err.println("Bank registration to NameServer failed");
            System.exit(-1);
        }

        System.err.println("Bank waiting for incoming connections...");

        Bank server = new Bank();

        try {
            server.listen(bankPort);
        }catch (IOException e){
            System.err.println("Bank unable to listen to given port");
            System.exit(-1);
        }
    }

    public void listen(int listenPort) throws java.io.IOException {

        ServerSocket serverSocket = serverSocket = new ServerSocket(listenPort);

        Socket connSocket = null;

        while(true) {

            connSocket = serverSocket.accept(); // block, waiting for a conn. request

            // At this point, we have a connection
            System.out.println("Connection accepted from: " + connSocket.getInetAddress().getHostName());

            // Now have a socket to use for communication
            // Create a PrintWriter and BufferedReader for interaction with our stream "true" means we flush the stream on newline
            PrintWriter out = new PrintWriter(connSocket.getOutputStream(), true);

            BufferedReader in = new BufferedReader(new InputStreamReader(connSocket.getInputStream()));

            String line = in.readLine();
            out.println(line);

            out.close();
            in.close();
            connSocket.close();
        }
    }
}
