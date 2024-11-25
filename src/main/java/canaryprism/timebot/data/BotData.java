package canaryprism.timebot.data;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.server.Server;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class BotData {
    private final Map<Server, ServerData> servers = new HashMap<>();
    
    public BotData() {
    }
    
    public BotData(JSONObject json, DiscordApi api) {
        for (var e : json.getJSONArray("servers")) {
            var data = new ServerData((JSONObject) e, api);
            servers.put(data.getServer(), data);
        }
    }
    
    public JSONObject toJSON() {
        synchronized (servers) {
            return new JSONObject()
                    .put("servers", new JSONArray(
                            servers.values()
                                    .parallelStream()
                                    .map(ServerData::toJSON)
                                    .collect(Collectors.toUnmodifiableSet())));
        }
    }
    
    public void putServerData(ServerData data) {
        Objects.requireNonNull(data, "data can't be null");
        synchronized (servers) {
            servers.put(data.getServer(), data);
        }
    }
    
    public Optional<ServerData> getServerData(Server server) {
        Objects.requireNonNull(server, "server can't be null");
        synchronized (servers) {
            return Optional.ofNullable(servers.get(server));
        }
    }
    
    public ServerData obtainServerData(Server server) {
        synchronized (servers) {
            return servers.computeIfAbsent(Objects.requireNonNull(server, "server cannot be null"), ServerData::new);
        }
    }
}
