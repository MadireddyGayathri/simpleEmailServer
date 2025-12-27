import java.io.*;
import java.net.*;

public class EmailReceiverServer {
    public static void main(String[] args) throws Exception {
        Database database = new Database();          // Create DB instance
        DatabaseCopy db = new DatabaseCopy(database); // Pass it to DatabaseCopy
        ServerSocket serverSocket = new ServerSocket(5000);

        System.out.println("Email Server started...");

        while (true) {
            Socket socket = serverSocket.accept();
            DataInputStream in = new DataInputStream(socket.getInputStream());

            String sender = in.readUTF();
            String receiver = in.readUTF();
            String subject = in.readUTF();
            String body = in.readUTF();

            db.sendMail(sender, receiver, subject, body); // Fixed method
            socket.close();
        }
    }
}
