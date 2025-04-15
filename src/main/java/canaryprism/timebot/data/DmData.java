package canaryprism.timebot.data;

import canaryprism.timebot.ResponderFlags;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.GroupChannel;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

public class DmData implements ChatData {

    private final long channel_id;
    private final Map<User, UserData> users = new HashMap<>();

    private volatile ResponderFlags forced_message_flags;

    public DmData(GroupChannel channel) {
        this.channel_id = Objects.requireNonNull(channel, "GroupChannel cannot be null").getIdLong();
    }

    public DmData(PrivateChannel channel) {
        this.channel_id = Objects.requireNonNull(channel, "PrivateChannel cannot be null").getIdLong();
    }

    public DmData(JSONObject json, JDA api) {
        this.channel_id = json.getLong("channel_id");

        for (var e : json.getJSONArray("users")) {
            var user_data = new UserData((JSONObject) e, api);
            users.put(user_data.getUser(), user_data);
        }

        this.forced_message_flags = Optional.ofNullable(json.optIntegerObject("forced_message_flags", null))
                .map(ResponderFlags::fromId)
                .orElse(null);
    }

    public JSONObject toJSON() {
        synchronized (users) {
            var json = new JSONObject()
                    .put("channel_id", channel_id)
                    .put("users", new JSONArray(users.values()
                            .parallelStream()
                            .map(UserData::toJSON)
                            .collect(Collectors.toUnmodifiableSet())));

            getForcedMessageFlag().ifPresent((e) -> json.put("forced_message_flags", e.getId()));

            return json;
        }
    }

    public long getChannelId() {
        return this.channel_id;
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
}
