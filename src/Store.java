import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Store implements IRPCServer {

    private HashMap<String, String> stockMap = new HashMap<String, String>();

    private Socket bankConnection;
    private Socket contentConnection;

    public Store() {
    }

    public void ConnectToBank(InetSocketAddress bankAddress) throws IOException {
        this.bankConnection = new Socket(bankAddress.getHostString(), bankAddress.getPort());
    }

    public void ConnectToContent(InetSocketAddress contentAddress) throws IOException {
        this.contentConnection = new Socket(contentAddress.getHostString(), contentAddress.getPort());
    }

    public void readContentFile(String filename) throws IOException {

        File file = new File(filename);

        BufferedReader reader = new BufferedReader(new FileReader(file));

        String line;

        while ((line = reader.readLine()) != null) {

            String itemId = line.trim().split(" ")[0];
            String price = line.trim().split(" ")[1];

            this.stockMap.put(itemId, price);
        }

        reader.close();
    }

    public String StockCatalogue() {

        StringBuilder catalogue = new StringBuilder();

        for (Map.Entry entry : stockMap.entrySet()) {

            catalogue.append(entry.getKey())
                    .append(" ")
                    .append(entry.getValue())
                    .append("\n");
        }

        return catalogue.toString();
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Invalid command line arguments for Content");
            System.exit(-1);
        }

        String storePortString = args[0];
        String stockFilename = args[1];
        String nameServerPortString = args[2];

        int storePort = Integer.parseInt(storePortString);
        int nameServerPort = Integer.parseInt(nameServerPortString);

        if (storePort < 0 || storePort > 65535) {
            System.err.println("Store unable to listen on given port");
            System.exit(-1);
        }

        Store storeServer = new Store();

        try {
            storeServer.readContentFile(stockFilename);
        } catch (IOException ignored) {
            System.err.println("Invalid command line arguments for Store");
            System.exit(-1);
        }

        if (!NameServer.Util.RegisterHost(nameServerPort, "Store", "127.0.0.1", storePortString)) {
            System.err.println("Store registration to NameServer failed");
            System.exit(-1);
        }

        InetSocketAddress bankAddress = NameServer.Util.LookUpHost(nameServerPort, "Bank");
        InetSocketAddress contentAddress = NameServer.Util.LookUpHost(nameServerPort, "Content");

        if (bankAddress == null) {
            System.err.println("Bank has not registered");
            System.exit(-1);
        }

        if (contentAddress == null) {
            System.err.println("Content has not registered");
            System.exit(-1);
        }

        try {
            storeServer.ConnectToBank(bankAddress);
        } catch (IOException ignored) {
            System.err.println("Unable to connect with Bank");
            System.exit(-1);
        }

        try {
            storeServer.ConnectToContent(contentAddress);
        } catch (IOException ignored) {
            System.err.println("Unable to connect with Content");
            System.exit(-1);
        }

        System.err.println("Store waiting for incoming connections...");

        try {
            NameServer.Util.RpcServer(new InetSocketAddress("127.0.0.1", storePort), storeServer);
        } catch (IOException e) {
            System.err.println("Content unable to listen to given port");
            System.exit(-1);
        }
    }

    @Override
    public String Respond(String message) {

        String regex = "(^catalogue|purchase[:]([0-9]*)[:]([0-9 ]*))";

        Matcher pattern = Pattern.compile(regex).matcher(message.trim());

        if (!pattern.matches()) {
            return "";
        }

        String command = pattern.group(1);

        if (command.equals("catalogue")) {
            return StockCatalogue();
        } else {

            String itemId = pattern.group(2); // optional groups
            String creditCard = pattern.group(3); // optional groups

            String price = stockMap.get(itemId);

            String bankRequest = itemId + ":" + price + ":" + creditCard;

            try {
                String bankResponse = NameServer.Util.RpcCallContinuous(bankConnection, bankRequest);

                if (bankResponse.equals("0")) { // NOT OK i.e. FAIL
                    return itemId + ":transaction aborted";
                }

                String contentResponse = NameServer.Util.RpcCallContinuous(contentConnection, itemId);

                String content = contentResponse.split(":")[1];

                if (content.equals("connection fails")) {
                    return itemId + ":transaction aborted";
                }

                return itemId + ":" + price + ":" + content;

            } catch (IOException ignored) {
                return itemId + ":transaction aborted";
            }
        }
    }
}
