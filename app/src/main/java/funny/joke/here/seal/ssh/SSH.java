package funny.joke.here.seal.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SSH {

    private final String id;
    private final String name;
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    private Session session;

    public SSH(String name, String host, int port, String username, String password) {
        this(UUID.randomUUID().toString(), name, host, port, username, password);
    }

    public SSH(String id, String name, String host, int port,
            String username, String password) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("host", host);
        obj.put("port", port);
        obj.put("username", username);
        obj.put("password", password);
        return obj;
    }

    public static SSH fromJson(JSONObject json) throws JSONException {
        return new SSH(
                json.getString("id"),
                json.getString("name"),
                json.getString("host"),
                json.getInt("port"),
                json.getString("username"),
                json.getString("password"));
    }

    public synchronized void openSession() throws JSchException {
        if (session != null && session.isConnected()) {
            return;
        }
        JSch jsch = new JSch();

        session = jsch.getSession(username, host, port);

        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");

        session.connect();
    }

    public synchronized void closeSession() {
        if (session != null) {
            session.disconnect();
            session = null;
        }
    }

    public synchronized boolean sessionActive() {
        return session != null && session.isConnected();
    }

    public synchronized String runCmd(String[] commands) throws JSchException, IOException {
        if (session == null || !session.isConnected()) {
            openSession();
        }
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        String commandString = String.join("\n", commands);
        channel.setCommand(commandString);

        InputStream in = channel.getInputStream();
        InputStream err = channel.getErrStream();

        channel.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String result = reader.lines().collect(Collectors.joining("\n"));

        BufferedReader errReader = new BufferedReader(new InputStreamReader(err));
        String errResult = errReader.lines().collect(Collectors.joining("\n"));

        channel.disconnect();
        reader.close();
        errReader.close();

        if (!errResult.isEmpty()) {
            if (result.isEmpty()) {
                return errResult;
            } else {
                return result + "\n" + errResult;
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return name + " (" + username + "@" + host + ":" + port + ")";
    }
}
