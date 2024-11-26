package canaryprism.timebot.data;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextableRegularServerChannel;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

public class BirthdayData {
    
    private volatile Integer year;
    private volatile int month;
    private volatile int day;
    private volatile int hour;
    
    private volatile int next_birthday_year;
    
    private volatile TextableRegularServerChannel channel;
    
    public BirthdayData(ZonedDateTime time, TextableRegularServerChannel channel) {
        this.setBirthday(time);
        this.setChannel(channel);
    }
    
    public BirthdayData(JSONObject json, DiscordApi api) {
        this.year = json.optIntegerObject("year", null);
        this.month = json.getInt("month");
        this.day = json.getInt("day");
        this.hour = json.getInt("hour");
        
        this.next_birthday_year = json.getInt("next_birthday_year");
        
        this.channel = ((TextableRegularServerChannel) api.getChannelById(json.getLong("channel")).orElseThrow());
    }
    
    public JSONObject toJSON() {
        var json = new JSONObject();
        
        Optional.ofNullable(year).ifPresent((e) -> json.put("year", e));
        
        return json
                .put("month", month)
                .put("day", day)
                .put("hour", hour)
                .put("next_birthday_year", next_birthday_year)
                .put("channel", channel.getId());
    }
    
    public synchronized void setBirthday(ZonedDateTime time) {
        var zone = time.getZone();
        time = time.withZoneSameInstant(ZoneOffset.UTC);
        
        this.year = (time.getYear() != -1) ? time.getYear() : null;
        this.month = time.getMonthValue();
        this.day = time.getDayOfMonth();
        this.hour = time.getHour();
        
        
        var now = ZonedDateTime.now(zone);
        
        this.next_birthday_year = now.getYear();
        
        var this_year_birthday = ZonedDateTime.of(now.getYear(), month, day,
                hour, 0, 0, 0, ZoneOffset.UTC);
        
        if (this_year_birthday.isBefore(now))
            next_birthday_year++;
    }
    
    public synchronized Instant getNextBirthday() {
        return ZonedDateTime.of(next_birthday_year, month, day,
                hour, 0, 0, 0, ZoneOffset.UTC).toInstant();
    }
    
    public synchronized void birthdayNotified() {
        next_birthday_year++;
    }
    
    public synchronized Optional<Integer> getAge() {
        var this_year = ZonedDateTime.now(ZoneOffset.UTC).getYear();
        
        return Optional.ofNullable(year).map((e) -> this_year - e);
    }
    
    public synchronized TextableRegularServerChannel getChannel() {
        return channel;
    }
    
    public synchronized void setChannel(TextableRegularServerChannel channel) {
        this.channel = Objects.requireNonNull(channel, "channel may not be null");
    }
}
