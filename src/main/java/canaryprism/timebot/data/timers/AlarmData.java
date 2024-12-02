package canaryprism.timebot.data.timers;

import canaryprism.timebot.data.UserData;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.json.JSONObject;

import java.time.*;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class AlarmData extends AbstractTimerData {
    
    protected volatile LocalTime local_time;
    
    protected final EnumSet<DayOfWeek> repeating_days = EnumSet.noneOf(DayOfWeek.class);
    
    public AlarmData(UserData owner, LocalTime time, TextChannel channel, String message) {
        super(owner, Instant.EPOCH, channel, message);
        this.local_time = time;
    }
    
    public AlarmData(JSONObject json, DiscordApi api, UserData owner) {
        super(json, api, owner);
        
        this.local_time = LocalTime.parse(json.getString("local_time"));
        
        for (var e : json.getJSONArray("repeating_days")) {
            var day = DayOfWeek.of(((int) e));
            repeating_days.add(day);
        }
    }
    
    @Override
    public synchronized JSONObject toJSON() {
        return super.toJSON()
                .put("local_time", local_time.toString())
                .put("repeating_days", repeating_days.stream().map(DayOfWeek::getValue).collect(Collectors.toUnmodifiableSet()));
    }
    
    public synchronized LocalTime getTime() {
        return local_time;
    }
    
    public synchronized void setTime(LocalTime time) {
        this.local_time = Objects.requireNonNull(time, "time can't be null");
        this.target = Instant.EPOCH;
        update();
    }
    
    private synchronized void update() {
        var opt_timezone = owner.getTimezone();
        
        if (opt_timezone.isEmpty()) {
            this.target = Instant.EPOCH;
            return;
        }
        
        if (this.target != Instant.EPOCH)
            return;
        
        var timezone = opt_timezone.get();
        
        var datetime = ZonedDateTime.of(LocalDate.now(timezone), local_time, timezone);
        
        if (datetime.isBefore(ZonedDateTime.now())) {
            datetime = datetime.plusDays(1);
        }
        
        if (!repeating_days.isEmpty()) {
            while (!repeating_days.contains(datetime.getDayOfWeek()))
                datetime = datetime.plusDays(1);
        }
        
        this.target = datetime.toInstant();
    }
    
    public synchronized Optional<Instant> getTargetTime() {
        update();
        return (this.target != Instant.EPOCH) ? Optional.of(this.target) : Optional.empty();
    }
    
    public EnumSet<DayOfWeek> getRepeatingDays() {
        synchronized (repeating_days) {
            return repeating_days.clone();
        }
    }
    
    public synchronized boolean addRepeatingDay(DayOfWeek day) {
        synchronized (repeating_days) {
            return repeating_days.add(Objects.requireNonNull(day, "day of week can't be null"));
        }
    }
    
    public synchronized boolean removeRepeatingDay(DayOfWeek day) {
        synchronized (repeating_days) {
            return repeating_days.remove(Objects.requireNonNull(day, "day of week can't be null"));
        }
    }
    
    @Override
    public synchronized boolean isActive() {
        return owner.hasAlarm(this);
    }
    
    @Override
    public synchronized void complete() {
        if (repeating_days.isEmpty()) {
            owner.removeAlarm(this);
        } else {
            this.target = Instant.EPOCH;
            update();
        }
    }
}
