package canaryprism.timebot.data.timers;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;

public class TimerData extends AbstractTimerData {
    
    protected final Duration duration;
    
    protected final TextChannel channel;
    
    protected final String message;
    
    public TimerData(Duration duration, TextChannel channel, String message) {
        super(Instant.now().plus(duration));
        
        this.duration = duration;
        this.channel = channel;
        this.message = message;
    }
    
    public TimerData(JSONObject json, DiscordApi api) {
        super(json);
        
        this.duration = Duration.parse(json.getString("duration"));
        
        this.channel = api.getTextChannelById(json.getLong("channel")).orElseThrow();
        
        this.message = json.getString("message");
    }
    
    @Override
    public JSONObject toJSON() {
        return super.toJSON()
                .put("duration", duration.toString())
                .put("channel", channel.getId())
                .put("message", message);
    }
    
    @Override
    public synchronized void setTargetTime(Instant target) {
        throw new UnsupportedOperationException("can't manually set target time on TimerData");
    }
    
    public Duration getDuration() {
        return duration;
    }
    
    public TextChannel getChannel() {
        return channel;
    }
    
    public String getMessage() {
        return message;
    }
}
