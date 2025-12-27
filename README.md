# SimpleEmailServer

A simple client–server email simulation implemented in Java with SQLite storage and an optional Python ML model to generate suggested email bodies from subjects.

## Architecture Overview

- Java server(s): TCP socket server (`EmailReceiverServer`) and HTTP server (`WebServer`) exposing REST endpoints and serving the web UI.
- SQLite database: stores users, inbox, and sent messages.
- Python ML: `ml_models/predict_email.py` suggests email bodies based on subject.
- Web UI: static SPA in `web/` to register/login, compose (with ML suggestion), and view inbox/sent.

## Sequence Diagram (high level)

The following Mermaid diagram shows the typical user flow when composing and sending an email using the web UI:

```mermaid
sequenceDiagram
    participant Browser
    participant WebServer
    participant Database
    participant ML as "Python ML"

    Browser->>WebServer: POST /api/login {email,password}
    WebServer->>Database: verify credentials
    Database-->>WebServer: auth result
    WebServer-->>Browser: {success, token}

    Browser->>WebServer: GET /api/ml?subject=Meeting
    WebServer->>ML: run predict_email.py --subject "Meeting"
    ML-->>WebServer: suggested body
    WebServer-->>Browser: {body: "..."}

    Browser->>WebServer: POST /api/send {to,subject,body} (X-Auth-Token)
    WebServer->>Database: insert inbox record (sender,receiver,subject,body)
    Database-->>WebServer: OK
    WebServer-->>Browser: {success:true}

    Browser->>WebServer: GET /api/inbox (X-Auth-Token)
    WebServer->>Database: SELECT inbox WHERE receiver=...
    Database-->>WebServer: [messages]
    WebServer-->>Browser: [messages]
```

How to run 

1. Compile: `del *.class; javac -cp ".;lib/*" *.java`
2. Start the Web UI + API: `java -cp ".;lib/*" WebServer`
3. Open browser: `http://localhost:8080` to interact with the UI
