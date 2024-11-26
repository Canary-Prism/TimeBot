package canaryprism.timebot.data;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.user.User;
import org.json.JSONObject;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class UserData {
    private final User user;
    
    private volatile ZoneId timezone;
    
    private volatile Optional<String> formatter_string = Optional.empty();
    private volatile DateTimeFormatter formatter;
    private volatile Locale locale;
    private volatile Boolean timezone_visible;
    
    private volatile BirthdayData birthday_data;
    
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
    }
    
    public synchronized JSONObject toJSON() {
        var json = new JSONObject();
        
        json.put("user_id", user.getId());
        
        getTimezone().ifPresent((timezone) -> json.put("timezone", timezone.getId()));
        
        formatter_string.ifPresent((formatter_string) -> json.put("formatter_string", formatter_string));
        
        getLocale().ifPresent((locale) -> json.put("locale", locale.toLanguageTag()));
        
        isTimezoneVisible().ifPresent((state) -> json.put("timezone_visible", state));
        
        getBirthdayData().ifPresent((birthday) -> json.put("birthday", birthday.toJSON()));
        
        return json;
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
    
    
    @Override
    public boolean equals(Object o) {
        return o instanceof UserData other && Objects.equals(this.user, other.user);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(user);
    }
}
