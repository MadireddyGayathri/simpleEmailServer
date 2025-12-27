import java.io.*;
import java.net.*;
import java.util.*;

public class TestWebApi {
    public static void main(String[] args) throws Exception {
        // Start WebServer in-process for reliable integration testing
        new Thread(() -> {
            try { WebServer.main(new String[0]); } catch (Exception e) { e.printStackTrace(); }
        }).start();
        Thread.sleep(1000); // wait for server

        String host = "http://localhost:8080";
        String u1 = "testA" + System.currentTimeMillis() + "@example.com";
        String u2 = "testB" + System.currentTimeMillis() + "@example.com";
        System.out.println("Registering " + u1 + " and " + u2);
        post(host + "/api/register", "email=" + URLEncoder.encode(u1, "UTF-8") + "&password=pass");
        post(host + "/api/register", "email=" + URLEncoder.encode(u2, "UTF-8") + "&password=pass");

        String loginResp = post(host + "/api/login", "email=" + URLEncoder.encode(u1, "UTF-8") + "&password=pass");
        String token = extract(loginResp, "token");
        if (token==null) { System.out.println("Login failed: " + loginResp); System.exit(1); }
        System.out.println("Got token: " + token);

        // send mail from u1 to u2
        String sendBody = "to=" + URLEncoder.encode(u2, "UTF-8") + "&subject=" + URLEncoder.encode("Hello","UTF-8") + "&body=" + URLEncoder.encode("Hi there","UTF-8");
        String sendResp = postWithToken(host + "/api/send", sendBody, token);
        System.out.println("sendResp=" + sendResp);

        // login u2 and check inbox
        String loginResp2 = post(host + "/api/login", "email=" + URLEncoder.encode(u2, "UTF-8") + "&password=pass");
        String token2 = extract(loginResp2, "token");
        String inbox = getWithToken(host + "/api/inbox", token2);
        System.out.println("inbox=" + inbox);
        if (inbox.contains("Hello") && inbox.contains("Hi there")) System.out.println("TEST PASSED"); else System.out.println("TEST FAILED");
    }

    static String post(String url, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes("UTF-8")); }
        return readAll(c);
    }
    static String postWithToken(String url, String body, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        c.setRequestProperty("X-Auth-Token", token);
        try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes("UTF-8")); }
        return readAll(c);
    }
    static String getWithToken(String url, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setRequestProperty("X-Auth-Token", token);
        return readAll(c);
    }
    static String readAll(HttpURLConnection c) throws Exception {
        int rc = c.getResponseCode();
        InputStream is = rc>=200 && rc<400 ? c.getInputStream() : c.getErrorStream();
        try (Scanner s = new Scanner(is).useDelimiter("\\A")) { return s.hasNext()?s.next():""; }
    }
    static String extract(String json, String k) {
        if (json==null) return null;
        int idx = json.indexOf('"'+k+'"');
        if (idx<0) return null;
        int c = json.indexOf('"', idx + k.length() + 3);
        if (c<0) return null;
        int d = json.indexOf('"', c+1);
        if (d<0) return null;
        return json.substring(c+1,d);
    }
}
