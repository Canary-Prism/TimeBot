package canaryprism.timebot.data;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.GroupChannel;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.interactions.Interaction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

public class BotData {
    private final Map<Long, ServerData> servers = new HashMap<>();
    private final Map<Long, DmData> dms = new HashMap<>();
    
    public BotData() {
    }
    
    public BotData(JSONObject json, JDA api) {
        for (var e : json.getJSONArray("servers")) {
            var data = new ServerData((JSONObject) e, api);
            servers.put(data.getServerId(), data);
        }
        for (var e : json.optJSONArray("dms", new JSONArray())) {
            var data = new DmData((JSONObject) e, api);
            dms.put(data.getChannelId(), data);
        }
    }
    
    public JSONObject toJSON() {
        synchronized (servers) {
            return new JSONObject()
                    .put("servers", new JSONArray(
                            servers.values()
                                    .parallelStream()
                                    .map(ServerData::toJSON)
                                    .collect(Collectors.toUnmodifiableSet())))
                    .put("dms", new JSONArray(
                            dms.values()
                                    .parallelStream()
                                    .map(DmData::toJSON)
                                    .collect(Collectors.toUnmodifiableSet())));
        }
    }
    
    public void putServerData(ServerData data) {
        Objects.requireNonNull(data, "data can't be null");
        synchronized (servers) {
            servers.put(data.getServerId(), data);
        }
    }
    
    public Optional<ServerData> getServerData(Guild server) {
        Objects.requireNonNull(server, "server can't be null");
        synchronized (servers) {
            return Optional.ofNullable(servers.get(server.getIdLong()));
        }
    }
    
    public ServerData obtainServerData(Guild server) {
        synchronized (servers) {
            return servers.computeIfAbsent(Objects.requireNonNull(server, "server cannot be null").getIdLong(), (e) -> new ServerData(server));
        }
    }
    
    public Set<? extends ServerData> getServers() {
        synchronized (servers) {
            return Set.copyOf(servers.values());
        }
    }

    public Optional<DmData> getDmData(PrivateChannel channel) {
        Objects.requireNonNull(channel, "channel can't be null");
        synchronized (dms) {
            return Optional.ofNullable(dms.get(channel.getIdLong()));
        }
    }
    public Optional<DmData> getDmData(GroupChannel channel) {
        Objects.requireNonNull(channel, "channel can't be null");
        synchronized (dms) {
            return Optional.ofNullable(dms.get(channel.getIdLong()));
        }
    }

    public DmData obtainDmData(PrivateChannel channel) {
        synchronized (servers) {
            return dms.computeIfAbsent(Objects.requireNonNull(channel, "channel cannot be null").getIdLong(), (e) -> new DmData(channel));
        }
    }
    public DmData obtainDmData(GroupChannel channel) {
        synchronized (servers) {
            return dms.computeIfAbsent(Objects.requireNonNull(channel, "channel cannot be null").getIdLong(), (e) -> new DmData(channel));
        }
    }

    public Optional<? extends ChatData> getChatData(Interaction payload) {
        if (payload.getGuild() != null)
            return this.getServerData(payload.getGuild());
        else
            return Optional.ofNullable(payload.getChannel())
                    .flatMap((e) -> switch (e) {
                        case PrivateChannel channel -> this.getDmData(channel);
                        case GroupChannel channel -> this.getDmData(channel);
                        default -> throw new IllegalArgumentException("what");
                    });
    }

    public ChatData obtainChatData(Interaction payload) {
        if (payload.getGuild() != null)
            return this.obtainServerData(payload.getGuild());
        else
            return Optional.ofNullable(payload.getChannel())
                    .map((e) -> switch (e) {
                        case PrivateChannel channel -> this.obtainDmData(channel);
                        case GroupChannel channel -> this.obtainDmData(channel);
                        default -> throw new IllegalArgumentException("what");
                    })
                    .orElseThrow();
    }
}
