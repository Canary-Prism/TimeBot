package canaryprism.timebot.data.timers;

import canaryprism.timebot.data.UserData;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Objects;

public abstract class AbstractTimerData {
    
    protected final UserData owner;
    
    protected volatile Instant target;
    
    protected volatile MessageChannel channel;
    
    protected volatile String message;
    
    public AbstractTimerData(UserData owner, Instant target, MessageChannel channel, String message) {
        this.owner = owner;
        this.target = target;
        this.channel = channel;
        this.message = message;
    }
    
    public AbstractTimerData(JSONObject json, JDA api, UserData owner) {
        this.owner = owner;
        this.target = Instant.ofEpochMilli(json.getLong("target"));
        this.channel = Objects.requireNonNull(api.getTextChannelById(json.getLong("channel")));
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
    
    public synchronized MessageChannel getChannel() {
        return channel;
    }
    
    public synchronized void setChannel(MessageChannel channel) {
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
