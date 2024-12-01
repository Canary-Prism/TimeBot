package canaryprism.timebot.data.timers;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;

public class TimerData extends AbstractTimerData {
    
    protected final Duration duration;
    
    public TimerData(Duration duration, TextChannel channel, String message) {
        super(Instant.now().plus(duration), channel, message);
        
        this.duration = duration;
    }
    
    public TimerData(JSONObject json, DiscordApi api) {
        super(json, api);
        
        this.duration = Duration.parse(json.getString("duration"));
    }
    
    @Override
    public JSONObject toJSON() {
        return super.toJSON()
                .put("duration", duration.toString());
    }
    
    @Override
    public Instant getTargetTime() {
        return target;
    }
    
    @Override
    public void setTargetTime(Instant target) {
        throw new UnsupportedOperationException("can't manually set target time on TimerData");
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
}
