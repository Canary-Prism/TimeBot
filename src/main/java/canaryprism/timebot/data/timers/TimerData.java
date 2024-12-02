package canaryprism.timebot.data.timers;

import canaryprism.timebot.data.UserData;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;

public class TimerData extends AbstractTimerData {
    
    protected final Duration duration;
    
    public TimerData(UserData owner, Duration duration, TextChannel channel, String message) {
        super(owner, Instant.now().plus(duration), channel, message);
        
        this.duration = duration;
    }
    
    public TimerData(JSONObject json, DiscordApi api, UserData owner) {
        super(json, api, owner);
        
        this.duration = Duration.parse(json.getString("duration"));
    }
    
    @Override
    public JSONObject toJSON() {
        return super.toJSON()
                .put("duration", duration.toString());
    }
    
    public Instant getTargetTime() {
        return target;
    }
    
    public Duration getDuration() {
        return duration;
    }
    
    @Override
    public TextChannel getChannel() {
        return channel;
    }
    
    @Override
    public void setChannel(TextChannel channel) {
        throw new UnsupportedOperationException("can't change channel on TimerData");
    }
    
    @Override
    public String getMessage() {
        return message;
    }
    
    @Override
    public void setMessage(String message) {
        throw new UnsupportedOperationException("can't change message on TimerData");
    }
    
    @Override
    public boolean isActive() {
        return owner.hasTimer(this);
    }
    
    @Override
    public void complete() {
        owner.removeTimer(this);
    }
}
