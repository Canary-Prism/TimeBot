package canaryprism.timebot.data.timers;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Objects;

public abstract class AbstractTimerData {
    
    protected volatile Instant target;
    
    protected volatile TextChannel channel;
    
    protected volatile String message;
    
    public AbstractTimerData(Instant target, TextChannel channel, String message) {
        this.target = target;
        this.channel = channel;
        this.message = message;
    }
    
    public AbstractTimerData(JSONObject json, DiscordApi api) {
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
    
    public synchronized Instant getTargetTime() {
        return target;
    }
    
    public synchronized void setTargetTime(Instant target) {
        this.target = Objects.requireNonNull(target, "target time can't be null");
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
}
