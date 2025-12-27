import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class WebServer {
    private static final int PORT = 8080;

    // Simple in-memory session store: token -> email
    private static final java.util.concurrent.ConcurrentMap<String,String> SESSIONS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        Database db = new Database();
        DatabaseCopy dbCopy = new DatabaseCopy(db);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new StaticHandler("web"));

        server.createContext("/api/register", new RegisterHandler(dbCopy));
        server.createContext("/api/login", new LoginHandler(dbCopy));
        server.createContext("/api/send", new SendHandler(dbCopy));
        server.createContext("/api/inbox", new InboxHandler(dbCopy));
        server.createContext("/api/sent", new SentHandler(dbCopy));
        server.createContext("/api/ml", new MlHandler());

        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("Web UI started at http://localhost:" + PORT);
    }

    // ---------------- Handlers ----------------
    static class RegisterHandler implements HttpHandler {
        private DatabaseCopy dbCopy;
        RegisterHandler(DatabaseCopy dbCopy) { this.dbCopy = dbCopy; }
        public void handle(HttpExchange ex) throws IOException {
            setCors(ex);
            if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(ex, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            String body = readRequest(ex);
            Map<String,String> m = parseBody(body);
            String email = m.get("email");
            String password = m.get("password");
            if (email == null || password == null) {
                sendResponse(ex,400,"{\"success\":false,\"message\":\"Missing fields\"}"); return;
            }
            // validate format and domain
            if (!dbCopy.isEmailFormatValid(email)) { sendResponse(ex,400,"{\"success\":false,\"message\":\"Invalid email format\"}"); return; }
            String domain = email.substring(email.indexOf('@') + 1);
            if (!dbCopy.isEmailDomainValid(domain)) { sendResponse(ex,400,"{\"success\":false,\"message\":\"Email domain not found\"}"); return; }

            boolean ok = dbCopy.register(email, password);
            if (ok) sendResponse(ex,200,"{\"success\":true}"); else sendResponse(ex,400,"{\"success\":false,\"message\":\"Registration failed (user exists or other error)\"}");
        }
    }

    static class LoginHandler implements HttpHandler {
        private DatabaseCopy dbCopy;
        LoginHandler(DatabaseCopy dbCopy) { this.dbCopy = dbCopy; }
        public void handle(HttpExchange ex) throws IOException {
            setCors(ex);
            if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(ex,405,"{\"error\":\"Method not allowed\"}");
                return;
            }
            String body = readRequest(ex);
            Map<String,String> m = parseBody(body);
            String email = m.get("email");
            String password = m.get("password");
            if (email == null || password == null) { sendResponse(ex,400,"{\"success\":false}"); return; }
            boolean ok = dbCopy.login(email, password);
            if (!ok) { sendResponse(ex,200,"{\"success\":false}"); return; }
            // generate token
            String token = java.util.UUID.randomUUID().toString();
            SESSIONS.put(token, email);
            sendResponse(ex,200,"{\"success\":true,\"token\":\"" + token + "\"}");
        }
    }

    static class SendHandler implements HttpHandler {
        private DatabaseCopy dbCopy;
        SendHandler(DatabaseCopy dbCopy) { this.dbCopy = dbCopy; }
        public void handle(HttpExchange ex) throws IOException {
            setCors(ex);
            if (!ex.getRequestMethod().equalsIgnoreCase("POST")) { sendResponse(ex,405,"{\"error\":\"Method not allowed\"}"); return; }
            String token = getTokenFromExchange(ex);
            String authEmail = SESSIONS.get(token);
            if (authEmail == null) { sendResponse(ex,401,"{\"success\":false,\"message\":\"Unauthorized\"}"); return; }
            String body = readRequest(ex);
            Map<String,String> m = parseBody(body);
            String to = m.get("to");
            String subject = m.get("subject");
            String message = m.get("body");
            if (to==null || subject==null || message==null) { sendResponse(ex,400,"{\"success\":false,\"message\":\"Missing fields\"}"); return; }
            // validate recipient format
            if (!dbCopy.isEmailFormatValid(to)) {
                sendResponse(ex,400,"{\"success\":false,\"message\":\"Invalid recipient email format\"}");
                return;
            }
            dbCopy.sendMail(authEmail, to, subject, message); // use authenticated email as sender
            sendResponse(ex,200,"{\"success\":true}");
        }
    }

    static class InboxHandler implements HttpHandler {
        private DatabaseCopy dbCopy;
        InboxHandler(DatabaseCopy dbCopy) { this.dbCopy = dbCopy; }
        public void handle(HttpExchange ex) throws IOException {
            setCors(ex);
            if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { sendResponse(ex,405,"{\"error\":\"Method not allowed\"}"); return; }
            String token = getTokenFromExchange(ex);
            String authEmail = SESSIONS.get(token);
            if (authEmail == null) { sendResponse(ex,401,"[]"); return; }
            String json = dbCopy.getInboxJson(authEmail);
            sendResponse(ex,200,json);
        }
    }

    static class SentHandler implements HttpHandler {
        private DatabaseCopy dbCopy;
        SentHandler(DatabaseCopy dbCopy) { this.dbCopy = dbCopy; }
        public void handle(HttpExchange ex) throws IOException {
            setCors(ex);
            if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { sendResponse(ex,405,"{\"error\":\"Method not allowed\"}"); return; }
            String token = getTokenFromExchange(ex);
            String authEmail = SESSIONS.get(token);
            if (authEmail == null) { sendResponse(ex,401,"[]"); return; }
            String json = dbCopy.getSentJson(authEmail);
            sendResponse(ex,200,json);
        }
    }

    static class MlHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            setCors(ex);
            if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { sendResponse(ex,405,"{\"error\":\"Method not allowed\"}"); return; }
            String query = ex.getRequestURI().getQuery();
            String subject = queryParam(query, "subject");
            if (subject == null) { sendResponse(ex,400,"{\"body\":\"\"}"); return; }
            String body = runMl(subject);
            sendResponse(ex,200,"{\"body\":\"" + escapeJson(body) + "\"}");
        }
    }

    // ---------------- Utilities ----------------
    private static String runMl(String subject) {
        List<String> cmd = Arrays.asList("python", "ml_models/predict_email.py", subject);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            boolean ok = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!ok) { p.destroy(); return ""; }
            try (InputStream is = p.getInputStream(); java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A")) {
                return s.hasNext() ? s.next().trim() : "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private static void setCors(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.add("Access-Control-Allow-Origin","*");
        h.add("Access-Control-Allow-Methods","GET,POST,OPTIONS");
        h.add("Access-Control-Allow-Headers","Content-Type,X-Auth-Token");
    }

    private static String getTokenFromExchange(HttpExchange ex) {
        String token = null;
        List<String> vals = ex.getRequestHeaders().get("X-Auth-Token");
        if (vals != null && !vals.isEmpty()) token = vals.get(0);
        if (token == null) {
            // fallback to query param token
            String q = ex.getRequestURI().getQuery();
            token = queryParam(q, "token");
        }
        return token;
    }

    private static String readRequest(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody(); java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }

    private static void sendResponse(HttpExchange ex, int code, String response) throws IOException {
        byte[] bytes = response.getBytes("UTF-8");
        ex.getResponseHeaders().add("Content-Type","application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static Map<String,String> parseBody(String body) {
        Map<String,String> m = new HashMap<>();
        if (body == null || body.isEmpty()) return m;
        // try form-encoded
        if (body.contains("=") && body.contains("&")) {
            String[] parts = body.split("&");
            for (String p: parts) {
                String[] kv = p.split("=",2);
                if (kv.length==2) m.put(urlDecode(kv[0]), urlDecode(kv[1]));
            }
            return m;
        }
        // crude JSON parse for simple flat objects {"k":"v"}
        body = body.trim();
        if (body.startsWith("{")) {
            for (String pair : body.substring(1, body.length()-1).split(",")) {
                String[] kv = pair.split(":",2);
                if (kv.length==2) {
                    String key = kv[0].trim().replaceAll("^\"|\"$", "");
                    String val = kv[1].trim().replaceAll("^\"|\"$", "");
                    m.put(key, val);
                }
            }
        }
        return m;
    }

    private static String urlDecode(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private static String queryParam(String q, String key) {
        if (q==null) return null;
        for (String pair : q.split("&")) {
            String[] kv = pair.split("=",2);
            if (kv.length==2 && kv[0].equals(key)) return java.net.URLDecoder.decode(kv[1]);
        }
        return null;
    }

    private static String escapeJson(String s) {
        if (s==null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");
    }

    // Simple static file handler
    static class StaticHandler implements HttpHandler {
        private Path root;
        StaticHandler(String rootDir) { this.root = java.nio.file.Paths.get(rootDir); }
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            Path file = root.resolve(path.substring(1)).normalize();
            if (!file.startsWith(root) || !Files.exists(file)) {
                String notFound = "<h1>404 Not Found</h1>";
                ex.getResponseHeaders().add("Content-Type","text/html; charset=utf-8");
                ex.sendResponseHeaders(404, notFound.getBytes().length);
                try (OutputStream os = ex.getResponseBody()) { os.write(notFound.getBytes()); }
                return;
            }
            String contentType = guessContentType(file);
            byte[] bytes = Files.readAllBytes(file);
            ex.getResponseHeaders().add("Content-Type", contentType);
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
        private String guessContentType(Path f) {
            String name = f.getFileName().toString().toLowerCase();
            if (name.endsWith(".html")) return "text/html; charset=utf-8";
            if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (name.endsWith(".css")) return "text/css; charset=utf-8";
            if (name.endsWith(".png")) return "image/png";
            return "application/octet-stream";
        }
    }
}
