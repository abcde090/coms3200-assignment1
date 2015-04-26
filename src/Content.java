import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;

public class Content implements IRPCServer {

    private HashMap<String, String> contentMap = new HashMap<String, String>();

    public Content() {
    }

    public void readContentFile(String filename) throws IOException {

        File file = new File(filename);

        BufferedReader reader = new BufferedReader(new FileReader(file));

        String line;

        while ((line = reader.readLine()) != null) {

            String itemId = line.split(" ")[0];
            String content = line.split(" ")[1];

            this.contentMap.put(itemId, content);
        }

        reader.close();

    }

    @Override
    public String Respond(String message) {
        return message + ":" + contentMap.getOrDefault(message, "connection fails");
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Invalid command line arguments for Content");
            System.exit(-1);
        }

        String contentPortString = args[0];
        String contentFilename = args[1];
        String nameServerPortString = args[2];

        int contentPort = Integer.parseInt(contentPortString);
        int nameServerPort = Integer.parseInt(nameServerPortString);

        if (contentPort < 0 || contentPort > 65535) {
            System.err.println("Content unable to listen on given port");
            System.exit(-1);
        }

        Content contentServer = new Content();

        try {
            contentServer.readContentFile(contentFilename);
        } catch (IOException ignored) {
            System.err.println("Invalid command line arguments for Content");
            System.exit(-1);
        }

        if (!NameServer.Util.RegisterHost(nameServerPort, "Content", "127.0.0.1", contentPortString)) {
            System.err.println("Content registration to NameServer failed");
            System.exit(-1);
        }

        System.err.println("Content waiting for incoming connections...");

        try {
            NameServer.Util.RpcServer(new InetSocketAddress("127.0.0.1", contentPort), contentServer);
        } catch (IOException e) {
            System.err.println("Content unable to listen to given port");
            System.exit(-1);
        }

    }
}
