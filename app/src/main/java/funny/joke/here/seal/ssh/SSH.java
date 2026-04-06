package funny.joke.here.seal.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class SSH {
    public static String runCmd() {
        JSch jsch = new JSch();

        Session session;
        try {
            // 10.0.2.2 == localhost on the host
            session = jsch.getSession("vm", "10.0.2.2", 2222);
        } catch (JSchException e) {
            return "invalid host/user";
        }

        session.setPassword("testVM");

        session.setConfig("StrictHostKeyChecking", "no");

        try {
            session.connect();
        } catch (JSchException e) {
            return String.format("failed to connect: %s", e);
        }

        BufferedReader reader;
        ChannelExec channel;

        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("uname -a");

            reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));

            channel.connect();
        } catch (JSchException e) {
            return "failed to open the channel";
        } catch (IOException e) {
            return "failed to create a reader";
        }

        String result = reader.lines().collect(Collectors.joining("\n"));

        channel.disconnect();
        session.disconnect();

        try {
            reader.close();
        } catch (IOException e) {
            return "couldn't close the reader";
        }

        return result;
    }
}
