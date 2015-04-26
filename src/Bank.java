import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bank implements IRPCServer {

    public Bank() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Invalid command line arguments for Bank");
            System.exit(-1);
        }

        String bankPortString = args[0];
        String nameServerPortString = args[1];

        int bankPort = Integer.parseInt(bankPortString);
        int nameServerPort = Integer.parseInt(nameServerPortString);

        if (bankPort < 0 || bankPort > 65535) {
            System.err.println("Bank unable to listen on given port");
            System.exit(-1);
        }

        if (!NameServer.Util.RegisterHost(nameServerPort, "Bank", "127.0.0.1", bankPortString)) {
            System.err.println("Bank registration to NameServer failed");
            System.exit(-1);
        }

        System.err.println("Bank waiting for incoming connections...");

        try {
            NameServer.Util.RpcServer(new InetSocketAddress("127.0.0.1", bankPort), new Bank());
        } catch (IOException e) {
            System.err.println("Bank unable to listen to given port");
            System.exit(-1);
        }
    }

    @Override
    public String Respond(String message) {

        // item-id : price : credit card number
        String regex = "([0-9]*)[:]([0-9]*.[0-9]*)[:]([0-9 ]*)";

        Matcher pattern = Pattern.compile(regex).matcher(message.trim());

        if (!pattern.matches()) {
            return "0";
        }

        long itemId = Long.parseLong(pattern.group(1));

        if (itemId % 2 == 0) { // even
            System.out.println(itemId);
            System.out.println("NOT OK");
            return "0";
        }

        System.out.println(itemId);
        System.out.println("OK");
        return "1";
    }
}
