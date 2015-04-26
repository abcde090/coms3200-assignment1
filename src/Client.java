import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Client {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Invalid command line arguments for Client");
            System.exit(-1);
        }

        String request = args[0];
        String nameServerPortString = args[1];

        int nameServerPort = Integer.parseInt(nameServerPortString);
        int requestNumber = Integer.parseInt(request);

        if (requestNumber < 0 || requestNumber > 10) {
            System.err.println("Invalid command line arguments for Client");
            System.exit(-1);
        }

        InetSocketAddress storeAddress = NameServer.Util.LookUpHost(nameServerPort, "Store");

        if (storeAddress == null) {
            System.err.println("Client unable to connect with NameServer");
            System.exit(-1);
        }

        Socket storeSocket = null;

        try {
            storeSocket = new Socket(storeAddress.getHostString(), storeAddress.getPort());
        } catch (IOException e) {
            System.err.println("Client unable to connect with Store");
            System.exit(-1);
        }

        if (requestNumber == 0) {

            try {

                String reply = NameServer.Util.RpcCallContinuous(storeSocket, "catalogue");

                String[] items = reply.split("\n");

                for (int i = 0; i < items.length; i++) {
                    System.out.println((i + 1) + ". " + items[i]);
                }

            } catch (IOException ignored) {
            }

        } else {

            try {

                String reply = NameServer.Util.RpcCallContinuous(storeSocket, "catalogue");

                String item = reply.split("\n")[requestNumber - 1];

                String itemId = item.split(" ")[0];

                reply = NameServer.Util.RpcCallContinuous(storeSocket, "purchase:" + itemId + ":" + "0000000000000000");

                String regex = "([0-9]*)[:]([0-9]*.[0-9]*)[:](.*)";

                Matcher pattern = Pattern.compile(regex).matcher(reply.trim());

                if (!pattern.matches()) {
                    System.out.println(itemId + " \"transaction aborted\"");
                } else {
                    String price = pattern.group(2);
                    String content = pattern.group(3);

                    System.out.println(itemId + " ($ " + price + ") CONTENT " + content);
                }

            } catch (IOException ignored) {
            }
        }

        try {
            storeSocket.close();
        } catch (IOException ignored) {
        }
    }
}
