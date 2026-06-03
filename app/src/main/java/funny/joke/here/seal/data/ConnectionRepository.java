package funny.joke.here.seal.data;

import android.content.Context;

import funny.joke.here.seal.ssh.SSH;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class ConnectionRepository {

    private static final String FILE_NAME = "connections.json";

    private final Context context;

    public ConnectionRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    private File getFile() {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    public List<SSH> loadAll() {
        List<SSH> result = new ArrayList<>();
        File file = getFile();
        if (!file.exists())
            return result;

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            JSONArray arr = new JSONArray(new String(bytes));
            for (int i = 0; i < arr.length(); i++) {
                result.add(SSH.fromJson(arr.getJSONObject(i)));
            }
        } catch (IOException | JSONException e) {

        }
        return result;
    }

    public void saveAll(List<SSH> connections) {
        JSONArray arr = new JSONArray();
        try {
            for (SSH conn : connections) {
                arr.put(conn.toJson());
            }
            try (FileWriter writer = new FileWriter(getFile())) {
                writer.write(arr.toString(2));
            }
        } catch (JSONException | IOException ignored) {}
    }

    public void add(SSH connection) {
        List<SSH> list = new ArrayList<>(loadAll());
        list.add(connection);
        saveAll(list);
    }

    public void remove(String id) {
        List<SSH> list = loadAll();
        list.removeIf(conn -> conn.getId().equals(id));
        saveAll(list);
    }
}
