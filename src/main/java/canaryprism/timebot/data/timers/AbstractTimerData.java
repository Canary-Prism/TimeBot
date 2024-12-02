package canaryprism.timebot.data.timers;

import canaryprism.timebot.data.UserData;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.json.JSONObject;

import java.time.Instant;

public abstract class AbstractTimerData {
    
    protected final UserData owner;
    
    protected volatile Instant target;
    
    protected volatile TextChannel channel;
    
    protected volatile String message;
    
    public AbstractTimerData(UserData owner, Instant target, TextChannel channel, String message) {
        this.owner = owner;
        this.target = target;
        this.channel = channel;
        this.message = message;
    }
    
    public AbstractTimerData(JSONObject json, DiscordApi api, UserData owner) {
        this.owner = owner;
        this.target = Instant.ofEpochMilli(json.getLong("target"));
        this.channel = api.getTextChannelById(json.getLong("channel")).orElseThrow();
        this.message = json.getString("message");
    }
    public synchronized JSONObject toJSON() {
        return new JSONObject()
                .put("target", target.toEpochMilli())
                .put("channel", channel.getId())
                .put("message", message);
    }
    
    public UserData getOwner() {
        return owner;
    }
    
    public synchronized TextChannel getChannel() {
        return channel;
    }
    
    public synchronized void setChannel(TextChannel channel) {
        this.channel = channel;
    }
    
    public synchronized String getMessage() {
        return message;
    }
    
    public synchronized void setMessage(String message) {
        this.message = message;
    }
    
    public abstract boolean isActive();
    
    public abstract void complete();
}
