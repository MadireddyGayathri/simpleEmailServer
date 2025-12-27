import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        Database database = new Database();
        DatabaseCopy dbCopy = new DatabaseCopy(database);
        SimpleEmailServer emailServer = new SimpleEmailServer(dbCopy);

        System.out.println("1. Register");
        System.out.println("2. Login");

        int option = sc.nextInt();
        sc.nextLine(); // clear buffer

        // ---------------- REGISTER ----------------
        if (option == 1) {
            System.out.print("Email: ");
            String email = sc.nextLine();

            System.out.print("Password: ");
            String password = sc.nextLine();

            boolean ok = dbCopy.register(email, password);
            if (ok) System.out.println("User registered successfully!"); else System.out.println("Registration failed (invalid email or user exists)");
        }

        // ---------------- LOGIN ----------------
        else if (option == 2) {

            System.out.print("Email: ");
            String email = sc.nextLine();

            System.out.print("Password: ");
            String password = sc.nextLine();

            if (dbCopy.login(email, password)) {
                System.out.println("Login successful!");

                boolean loggedIn = true;

                while (loggedIn) {
                    System.out.println("\n--- MENU ---");
                    System.out.println("1. Send Email");
                    System.out.println("2. View Inbox");
                    System.out.println("3. View Sent Emails");
                    System.out.println("4. Logout");

                    int choice = sc.nextInt();
                    sc.nextLine(); // clear buffer

                    switch (choice) {
                        case 1: // Send Email
                            System.out.print("Receiver: ");
                            String receiver = sc.nextLine();

                            // validate recipient format before proceeding
                            if (!dbCopy.isEmailFormatValid(receiver)) {
                                System.out.println("Invalid recipient email format. Send cancelled.");
                                break;
                            }

                            System.out.print("Subject: ");
                            String subject = sc.nextLine();

                            System.out.print("Generate body using ML? (y/n): ");
                            String useMl = sc.nextLine().trim().toLowerCase();
                            String body = "";

                            if (useMl.equals("y") || useMl.equals("yes")) {
                                String suggested = getSuggestedBody(subject);
                                if (suggested != null && !suggested.isEmpty()) {
                                    System.out.println("Suggested body:\n" + suggested);
                                    System.out.print("Accept suggested body? (y/n): ");
                                    String accept = sc.nextLine().trim().toLowerCase();
                                    if (accept.equals("y") || accept.equals("yes")) {
                                        body = suggested;
                                    } else {
                                        System.out.print("Body: ");
                                        body = sc.nextLine();
                                    }
                                } else {
                                    System.out.println("ML suggestion unavailable. Enter body manually.");
                                    System.out.print("Body: ");
                                    body = sc.nextLine();
                                }
                            } else {
                                System.out.print("Body: ");
                                body = sc.nextLine();
                            }

                            emailServer.sendEmail(email, receiver, subject, body);
                            break;

                        case 2: // View Inbox
                            dbCopy.viewInbox(email);
                            break;

                        case 3: // View Sent Emails
                            dbCopy.viewSent(email);
                            break;

                        case 4: // Logout
                            loggedIn = false;
                            System.out.println("Logged out successfully!");
                            break;

                        default:
                            System.out.println("Invalid option. Try again.");
                    }
                }

            } else {
                System.out.println("Invalid login credentials!");
            }
        }

        else {
            System.out.println("Invalid option!");
        }

        sc.close();
    }

    private static String getSuggestedBody(String subject) {
        ProcessBuilder pb = new ProcessBuilder("python", "ml_models/predict_email.py", subject);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroy();
                System.out.println("ML process timed out");
                return null;
            }
            try (java.io.InputStream is = p.getInputStream(); java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A")) {
                return s.hasNext() ? s.next().trim() : null;
            }
        } catch (Exception e) {
            System.out.println("Failed to run ML predictor: " + e.getMessage());
            return null;
        }
    }
}


