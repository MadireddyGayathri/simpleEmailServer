public class DatabaseCopy {

    private Database database;

    public DatabaseCopy(Database database) {
        this.database = database;
    }

    public boolean register(String email, String password) {
        return database.registerUser(email, password);
    }

    public boolean login(String email, String password) {
        return database.login(email, password);
    }

    // Expose validation helpers for WebServer
    public boolean isEmailFormatValid(String email) { return database.isValidEmailFormat(email); }
    public boolean isEmailDomainValid(String domain) { return database.hasValidDomain(domain); }

    public void viewInbox(String email) {
        database.showInbox(email);
    }

    public void sendMail(String sender, String receiver, String subject, String body) {
        database.storeEmail(sender, receiver, subject, body);
    }

    public void viewSent(String email) {
        database.showSent(email);
    }

    // JSON-returning wrappers used by WebServer
    public String getInboxJson(String email) {
        return database.getInboxJson(email);
    }

    public String getSentJson(String email) {
        return database.getSentJson(email);
    }

}
