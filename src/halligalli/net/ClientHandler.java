package halligalli.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * A thread handling one connected client. Reads commands and forwards them to the
 * {@link GameServer}, and writes server messages out to that socket.
 */
public class ClientHandler extends Thread {

    private final GameServer server;
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private volatile boolean closed = false;

    public ClientHandler(GameServer server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(
                new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        setDaemon(true);
    }

    /** Sends one line to this client. */
    public synchronized void send(String line) {
        if (!closed) {
            out.println(line);
        }
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                server.handleCommand(this, line.trim());
            }
        } catch (IOException e) {
            // connection closed
        } finally {
            close();
            server.onDisconnect(this);
        }
    }

    public void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
