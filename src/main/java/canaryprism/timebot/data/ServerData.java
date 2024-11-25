package canaryprism.timebot.data;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

public class ServerData {
    
    private final Server server;
    private final Map<User, UserData> users = new HashMap<>();
    
    private volatile MessageFlag forced_message_flags;
    
    public ServerData(Server server) {
        this.server = Objects.requireNonNull(server, "server cannot be null");
    }
    
    public ServerData(JSONObject json, DiscordApi api) {
        this.server = api.getServerById(json.getLong("server_id")).orElseThrow();
        
        for (var e : json.getJSONArray("users")) {
            var user_data = new UserData((JSONObject) e, api);
            users.put(user_data.getUser(), user_data);
        }
        
        this.forced_message_flags = Optional.ofNullable(json.optIntegerObject("forced_message_flags", null))
                .map(MessageFlag::getFlagTypeById)
                .orElse(null);
    }
    
    public JSONObject toJSON() {
        synchronized (users) {
            var json = new JSONObject()
                    .put("server_id", server.getId())
                    .put("users", new JSONArray(users.values().parallelStream().map(UserData::toJSON).collect(Collectors.toUnmodifiableSet())));
            
            getForcedMessageFlag().ifPresent((e) -> json.put("forced_message_flags", e.getId()));
            
            return json;
        }
    }
    
    public Server getServer() {
        return this.server;
    }
    
    public void putUserData(UserData data) {
        Objects.requireNonNull(data, "data can't be null");
        synchronized (users) {
            users.put(data.getUser(), data);
        }
    }
    
    public Optional<UserData> getUserData(User user) {
        Objects.requireNonNull(user, "user can't be null");
        synchronized (users) {
            return Optional.ofNullable(users.get(user));
        }
    }
    
    public UserData obtainUserData(User user) {
        synchronized (users) {
            return users.computeIfAbsent(Objects.requireNonNull(user, "user can't be null"), UserData::new);
        }
    }
    
    public void forceMessageFlag(MessageFlag forced_message_flags) {
        synchronized (this) {
            this.forced_message_flags = forced_message_flags;
        }
    }
    
    public Optional<MessageFlag> getForcedMessageFlag() {
        synchronized (this) {
            return Optional.ofNullable(this.forced_message_flags);
        }
    }
}
