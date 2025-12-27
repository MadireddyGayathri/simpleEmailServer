public class SimpleEmailServer {

    private DatabaseCopy dbCopy;

    public SimpleEmailServer(DatabaseCopy dbCopy) {
        this.dbCopy = dbCopy;
    }

    public void sendEmail(String sender, String receiver, String subject, String body) {
        dbCopy.sendMail(sender, receiver, subject, body);
        System.out.println("Email sent successfully!");
    }
}
