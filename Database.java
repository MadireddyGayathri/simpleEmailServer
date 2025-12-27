import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class Database {
    private static final String DB_URL = "jdbc:sqlite:Users.db";
    private static final SecureRandom RANDOM = new SecureRandom();

    public Database() {
        try {
            // load driver
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        createTables();
        addTimestampColumnIfNotExists(); // Ensure timestamp exists for legacy DBs
    }

    private void createTables() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            // Users table (email primary key, password stored as salt:hash)
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "email TEXT PRIMARY KEY, password TEXT)"
            );

            // Inbox table with id and timestamp
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS inbox (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender TEXT, " +
                "receiver TEXT, " +
                "subject TEXT, " +
                "body TEXT, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)"
            );

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Safely add timestamp column if it doesn't exist (legacy DBs)
    private void addTimestampColumnIfNotExists() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                "ALTER TABLE inbox ADD COLUMN timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
            );
        } catch (SQLException e) {
            // Ignore error if column already exists or ALTER unsupported
            String msg = e.getMessage();
            if (msg == null || (!msg.contains("duplicate column name") && !msg.contains("duplicate column"))) {
                // If it's a different error, print for debugging
                // (Most common: "duplicate column name: timestamp")
                // Silently ignore otherwise to keep compatibility
            }
        }
    }

    // ---------------- USER METHODS ----------------
    // Returns true if registration succeeded, false otherwise
    public boolean registerUser(String email, String password) {
        if (!isValidEmailFormat(email)) {
            System.out.println("Invalid email format");
            return false;
        }
        String domain = email.substring(email.indexOf('@') + 1);
        if (!hasValidDomain(domain)) {
            System.out.println("Email domain seems invalid or unreachable");
            return false;
        }

        String hashed = null;
        try {
            hashed = hashPassword(password);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Password hashing failed: " + e.getMessage());
            return false;
        }

        String sql = "INSERT INTO users (email, password) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, hashed);
            ps.executeUpdate();
            System.out.println("User registered successfully");
            return true;
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("unique") || msg.contains("constraint")) {
                System.out.println("User already exists");
                return false;
            } else {
                e.printStackTrace();
                return false;
            }
        }
    }

    public boolean login(String email, String password) {
        String sql = "SELECT password FROM users WHERE email=?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String stored = rs.getString("password");

                // If stored contains salt:hash format, verify
                if (stored != null && stored.contains(":")) {
                    boolean ok = verifyPassword(password, stored);
                    return ok;
                }

                // Backwards compatibility: some entries may be plaintext
                if (stored != null && stored.equals(password)) {
                    // Upgrade to hashed password for security
                    try (PreparedStatement update = conn.prepareStatement("UPDATE users SET password=? WHERE email=?")) {
                        update.setString(1, hashPassword(password));
                        update.setString(2, email);
                        update.executeUpdate();
                    } catch (NoSuchAlgorithmException ex) {
                        // ignore upgrade failure
                    }
                    return true;
                }

                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ---------------- EMAIL METHODS ----------------
    public void storeEmail(String sender, String receiver,
                           String subject, String body) {
        String sql = "INSERT INTO inbox (sender, receiver, subject, body) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sender);
            ps.setString(2, receiver);
            ps.setString(3, subject);
            ps.setString(4, body);
            ps.executeUpdate();
            System.out.println("Email stored in DB");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void showInbox(String email) {
        String sql = "SELECT sender, subject, body, timestamp FROM inbox WHERE receiver=? ORDER BY timestamp DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("\n📥 INBOX:");
                System.out.println("--------------------------------------------------");

                boolean empty = true;
                while (rs.next()) {
                    empty = false;
                    System.out.println("From   : " + rs.getString("sender"));
                    System.out.println("Subject: " + rs.getString("subject"));
                    System.out.println("Message: " + rs.getString("body"));
                    System.out.println("Time   : " + rs.getString("timestamp"));
                    System.out.println("--------------------------------------------------");
                }

                if (empty) System.out.println("No emails found.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void showSent(String email) {
        String sql = "SELECT receiver, subject, body, timestamp FROM inbox WHERE sender=? ORDER BY timestamp DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("\n📤 SENT MAILS:");
                System.out.println("--------------------------------------------------");

                boolean empty = true;
                while (rs.next()) {
                    empty = false;
                    System.out.println("To     : " + rs.getString("receiver"));
                    System.out.println("Subject: " + rs.getString("subject"));
                    System.out.println("Message: " + rs.getString("body"));
                    System.out.println("Time   : " + rs.getString("timestamp"));
                    System.out.println("--------------------------------------------------");
                }

                if (empty) System.out.println("No sent emails.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Returns inbox as JSON array of objects: [{"from":"...","subject":"...","body":"...","time":"..."},...]
    public String getInboxJson(String email) {
        String sql = "SELECT sender, subject, body, timestamp FROM inbox WHERE receiver=? ORDER BY timestamp DESC";
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) sb.append(',');
                    first = false;
                    String from = escapeJson(rs.getString("sender"));
                    String subject = escapeJson(rs.getString("subject"));
                    String body = escapeJson(rs.getString("body"));
                    String time = escapeJson(rs.getString("timestamp"));
                    sb.append("{\"from\":\"").append(from).append("\",\"subject\":\"").append(subject)
                      .append("\",\"body\":\"").append(body).append("\",\"time\":\"").append(time).append("\"}");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "[]";
        }
        sb.append("]");
        return sb.toString();
    }

    // Returns sent mails as JSON array of objects: [{"to":"...","subject":"...","body":"...","time":"..."},...]
    public String getSentJson(String email) {
        String sql = "SELECT receiver, subject, body, timestamp FROM inbox WHERE sender=? ORDER BY timestamp DESC";
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) sb.append(',');
                    first = false;
                    String to = escapeJson(rs.getString("receiver"));
                    String subject = escapeJson(rs.getString("subject"));
                    String body = escapeJson(rs.getString("body"));
                    String time = escapeJson(rs.getString("timestamp"));
                    sb.append("{\"to\":\"").append(to).append("\",\"subject\":\"").append(subject)
                      .append("\",\"body\":\"").append(body).append("\",\"time\":\"").append(time).append("\"}");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "[]";
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // ---------------- Password hashing helpers ----------------
    private String hashPassword(String password) throws NoSuchAlgorithmException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltHex = bytesToHex(salt);
        String hashHex = bytesToHex(sha256((saltHex + password).getBytes()));
        return saltHex + ":" + hashHex;
    }

    private boolean verifyPassword(String password, String stored) {
        try {
            String[] parts = stored.split(":");
            if (parts.length != 2) return false;
            String saltHex = parts[0];
            String hashHex = parts[1];
            String computed = bytesToHex(sha256((saltHex + password).getBytes()));
            return computed.equals(hashHex);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private byte[] sha256(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(input);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ---------------- Email / Domain validation helpers ----------------
    public boolean isValidEmailFormat(String email) {
        if (email == null) return false;
        return email.matches("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    public boolean hasValidDomain(String domain) {
        if (domain == null || domain.isEmpty()) return false;
        try {
            // Check MX records
            java.util.Hashtable<String, String> env = new java.util.Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            javax.naming.directory.DirContext ictx = new javax.naming.directory.InitialDirContext(env);
            javax.naming.directory.Attributes attrs = ictx.getAttributes(domain, new String[] {"MX"});
            javax.naming.directory.Attribute attr = attrs.get("MX");
            if (attr != null && attr.size() > 0) return true;
            // fallback: check A record
            attrs = ictx.getAttributes(domain, new String[] {"A"});
            attr = attrs.get("A");
            return attr != null && attr.size() > 0;
        } catch (Exception e) {
            // DNS lookup might fail in some environments; treat as unknown/unreachable
            return false;
        }
    }
}

