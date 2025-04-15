package canaryprism.timebot.data;

import canaryprism.timebot.ResponderFlags;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

public class ServerData implements ChatData {
    
    private final long server_id;
    private final Map<User, UserData> users = new HashMap<>();
    
    private volatile ResponderFlags forced_message_flags;
    
    private final Set<GuildMessageChannel> allowed_birthday_channels = new HashSet<>();

    private volatile Boolean allow_custom_messages;
    
    public ServerData(Guild server) {
        this.server_id = Objects.requireNonNull(server, "server cannot be null").getIdLong();
    }
    
    public ServerData(JSONObject json, JDA api) {
        this.server_id = json.getLong("server_id");
        
        for (var e : json.getJSONArray("users")) {
            var user_data = new UserData((JSONObject) e, api);
            users.put(user_data.getUser(), user_data);
        }
        
        this.forced_message_flags = Optional.ofNullable(json.optIntegerObject("forced_message_flags", null))
                .map(ResponderFlags::fromId)
                .orElse(null);
        
        for (var e : json.getJSONArray("allowed_birthday_channels")) {
            try {
                var channel = (GuildMessageChannel) Objects.requireNonNull(api.getGuildChannelById(((long) e)));
                allowed_birthday_channels.add(channel);
            } catch (NullPointerException n) {
                // do nothing ig
            }
        }

        this.allow_custom_messages = json.optBooleanObject("allow_custom_messages", null);
    }
    
    public JSONObject toJSON() {
        synchronized (users) {
            var json = new JSONObject()
                    .put("server_id", server_id)
                    .put("users", new JSONArray(users.values()
                            .parallelStream()
                            .map(UserData::toJSON)
                            .collect(Collectors.toUnmodifiableSet())))
                    .put("allowed_birthday_channels", new JSONArray(allowed_birthday_channels.stream()
                            .map(GuildMessageChannel::getIdLong)
                            .collect(Collectors.toUnmodifiableSet())));

            getForcedMessageFlag().ifPresent((e) -> json.put("forced_message_flags", e.getId()));

            allowsCustomMessages().ifPresent((e) -> json.put("allow_custom_messages", e));
            
            return json;
        }
    }
    
    public long getServerId() {
        return this.server_id;
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
    
    public void forceMessageFlag(ResponderFlags forced_message_flags) {
        synchronized (this) {
            this.forced_message_flags = forced_message_flags;
        }
    }
    
    public Optional<ResponderFlags> getForcedMessageFlag() {
        synchronized (this) {
            return Optional.ofNullable(this.forced_message_flags);
        }
    }
    
    public Set<GuildMessageChannel> getAllowedBirthdayChannels() {
        synchronized (allowed_birthday_channels) {
            return Set.copyOf(allowed_birthday_channels);
        }
    }
    
    public boolean addAllowedBirthdayChannel(GuildMessageChannel channel) {
        Objects.requireNonNull(channel, "channel can't be null");
        synchronized (allowed_birthday_channels) {
            return allowed_birthday_channels.add(channel);
        }
    }
    
    public boolean removeAllowedBirthdayChannel(GuildMessageChannel channel) {
        Objects.requireNonNull(channel, "channel can't be null");
        synchronized (allowed_birthday_channels) {
            return allowed_birthday_channels.remove(channel);
        }
    }
    
    public boolean isAllowedBirthdayChannel(GuildMessageChannel channel) {
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
