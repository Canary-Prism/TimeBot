package canaryprism.timebot.data;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextableRegularServerChannel;
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
    
    private final Set<TextableRegularServerChannel> allowed_birthday_channels = new HashSet<>();

    private volatile Boolean allow_custom_messages;
    
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
        
        for (var e : json.getJSONArray("allowed_birthday_channels")) {
            var channel = (TextableRegularServerChannel) server.getChannelById(((long) e)).orElseThrow();
            allowed_birthday_channels.add(channel);
        }

        this.allow_custom_messages = json.optBooleanObject("allow_custom_messages", null);
    }
    
    public JSONObject toJSON() {
        synchronized (users) {
            var json = new JSONObject()
                    .put("server_id", server.getId())
                    .put("users", new JSONArray(users.values()
                            .parallelStream()
                            .map(UserData::toJSON)
                            .collect(Collectors.toUnmodifiableSet())))
                    .put("allowed_birthday_channels", new JSONArray(allowed_birthday_channels.stream()
                            .map(TextableRegularServerChannel::getId)
                            .collect(Collectors.toUnmodifiableSet())));
            
            getForcedMessageFlag().ifPresent((e) -> json.put("forced_message_flags", e.getId()));

            allowsCustomMessages().ifPresent((e) -> json.put("allow_custom_messages", e));
            
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
    
    public Set<? extends UserData> getUsers() {
        synchronized (users) {
            return Set.copyOf(users.values());
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
    
    public Set<TextableRegularServerChannel> getAllowedBirthdayChannels() {
        synchronized (allowed_birthday_channels) {
            return Set.copyOf(allowed_birthday_channels);
        }
    }
    
    public boolean addAllowedBirthdayChannel(TextableRegularServerChannel channel) {
        Objects.requireNonNull(channel, "channel can't be null");
        synchronized (allowed_birthday_channels) {
            return allowed_birthday_channels.add(channel);
        }
    }
    
    public boolean removeAllowedBirthdayChannel(TextableRegularServerChannel channel) {
        Objects.requireNonNull(channel, "channel can't be null");
        synchronized (allowed_birthday_channels) {
            return allowed_birthday_channels.remove(channel);
        }
    }
    
    public boolean isAllowedBirthdayChannel(TextableRegularServerChannel channel) {
        Objects.requireNonNull(channel, "channel can't be null");
        synchronized (allowed_birthday_channels) {
            return allowed_birthday_channels.contains(channel);
        }
    }

    public Optional<Boolean> allowsCustomMessages() {
        return Optional.ofNullable(allow_custom_messages);
    }

    public void setAllowsCustomMessages(boolean allow_custom_messages) {
        this.allow_custom_messages = allow_custom_messages;
    }
}
