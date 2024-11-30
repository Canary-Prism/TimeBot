package canaryprism.timebot.data.timers;

import org.json.JSONObject;

import java.time.Instant;
import java.util.Objects;

public abstract class AbstractTimerData {
    
    protected volatile Instant target;
    
    public AbstractTimerData(Instant target) {
        this.target = target;
    }
    
    public AbstractTimerData(JSONObject json) {
        this.target = Instant.ofEpochMilli(json.getLong("target"));
    }
    public JSONObject toJSON() {
        return new JSONObject()
                .put("target", target.toEpochMilli());
    }
    
    public synchronized Instant getTargetTime() {
        return target;
    }
    
    public synchronized void setTargetTime(Instant target) {
        this.target = Objects.requireNonNull(target, "target time can't be null");
    }
}
