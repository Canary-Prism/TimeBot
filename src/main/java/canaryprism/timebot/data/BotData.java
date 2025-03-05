package canaryprism.timebot.data;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

public class BotData {
    private final Map<Guild, ServerData> servers = new HashMap<>();
    
    public BotData() {
    }
    
    public BotData(JSONObject json, JDA api) {
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
    
    public Optional<ServerData> getServerData(Guild server) {
        Objects.requireNonNull(server, "server can't be null");
        synchronized (servers) {
            return Optional.ofNullable(servers.get(server));
        }
    }
    
    public ServerData obtainServerData(Guild server) {
        synchronized (servers) {
            return servers.computeIfAbsent(Objects.requireNonNull(server, "server cannot be null"), ServerData::new);
        }
    }
    
    public Set<? extends ServerData> getServers() {
        synchronized (servers) {
            return Set.copyOf(servers.values());
        }
    }
}
