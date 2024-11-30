package canaryprism.timebot.data;

import canaryprism.timebot.data.timers.TimerData;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.user.User;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class UserData {
    private final User user;
    
    private volatile ZoneId timezone;
    
    private volatile Optional<String> formatter_string = Optional.empty();
    private volatile DateTimeFormatter formatter;
    private volatile Locale locale;
    private volatile Boolean timezone_visible;
    
    private volatile BirthdayData birthday_data;
    
    private final ArrayList<TimerData> timers = new ArrayList<>();
    
    public UserData(User user) {
        this.user = Objects.requireNonNull(user, "user cannot be null");
    }
    
    public UserData(JSONObject json, DiscordApi api) {
        this.user = api.getUserById(json.getLong("user_id")).join();
        
        this.timezone = Optional.ofNullable(json.optString("timezone", null)).map(ZoneId::of).orElse(null);
        
        this.formatter_string = Optional.ofNullable(json.optString("formatter_string", null));
        
        this.formatter = formatter_string.map(DateTimeFormatter::ofPattern)
                .orElse(null);
        
        this.locale = Optional.ofNullable(json.optString("locale", null))
                .map(Locale::forLanguageTag)
                .orElse(null);
        
        this.timezone_visible = json.optBooleanObject("timezone_visible", null);
        
        this.birthday_data = Optional.ofNullable(json.optJSONObject("birthday"))
                .map((e) -> new BirthdayData(e, api))
                .orElse(null);
        
        for (var e : json.optJSONArray("timers", new JSONArray())) {
            var timer = new TimerData((JSONObject) e, api);
            timers.add(timer);
        }
    }
    
    public synchronized JSONObject toJSON() {
        var json = new JSONObject();
        
        json.put("user_id", user.getId());
        
        getTimezone().ifPresent((timezone) -> json.put("timezone", timezone.getId()));
        
        formatter_string.ifPresent((formatter_string) -> json.put("formatter_string", formatter_string));
        
        getLocale().ifPresent((locale) -> json.put("locale", locale.toLanguageTag()));
        
        isTimezoneVisible().ifPresent((state) -> json.put("timezone_visible", state));
        
        getBirthdayData().ifPresent((birthday) -> json.put("birthday", birthday.toJSON()));
        
        json.put("timers", timers.stream().map(TimerData::toJSON).toList());
        
        return json;
    }
    
    @Override
    public boolean equals(Object o) {
        return o instanceof UserData other && Objects.equals(this.user, other.user);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(user);
    }
    
    public User getUser() {
        return this.user;
    }
    
    public synchronized Optional<ZoneId> getTimezone() {
        return Optional.ofNullable(timezone);
    }
    
    public synchronized void setTimezone(ZoneId timezone) {
        this.timezone = timezone;
    }
    
    public synchronized Optional<DateTimeFormatter> getFormatter() {
        return Optional.ofNullable(formatter);
    }
    
    public synchronized Optional<String> getFormatterPattern() {
        return formatter_string;
    }
    
    public synchronized void setFormatter(String formatter_pattern) {
        this.formatter_string = Optional.ofNullable(formatter_pattern);
        
        this.formatter = formatter_string.map(DateTimeFormatter::ofPattern).orElse(null);
    }
    
    public synchronized Optional<Locale> getLocale() {
        return Optional.ofNullable(locale);
    }
    
    public synchronized void setLocale(Locale locale) {
        this.locale = locale;
    }
    
    public synchronized Optional<Boolean> isTimezoneVisible() {
        return Optional.ofNullable(timezone_visible);
    }
    
    public synchronized void setTimezoneVisible(Boolean timezone_visible) {
        this.timezone_visible = timezone_visible;
    }
    
    public synchronized Optional<BirthdayData> getBirthdayData() {
        return Optional.ofNullable(birthday_data);
    }
    
    public synchronized void setBirthdayData(BirthdayData birthday_data) {
        this.birthday_data = birthday_data;
    }
    
    public List<TimerData> getTimers() {
        synchronized (timers) {
            return List.copyOf(timers);
        }
    }
    
    public Optional<TimerData> getTimer(int index) {
        synchronized (timers) {
            if (index >= 0 && index < timers.size())
                return Optional.of(timers.get(index));
            return Optional.empty();
        }
    }
    
    public void addTimer(TimerData data) {
        synchronized (timers) {
            timers.add(Objects.requireNonNull(data, "timer data can't be null"));
        }
    }
    
    public void removeTimer(TimerData data) {
        synchronized (timers) {
            timers.remove(Objects.requireNonNull(data, "timer data can't be null"));
        }
    }
    
    public boolean hasTimer(TimerData data) {
        synchronized (timers) {
            return timers.contains(Objects.requireNonNull(data, "timer data can't be null"));
        }
    }
    
}
