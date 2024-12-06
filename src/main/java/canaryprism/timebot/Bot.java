package canaryprism.timebot;

import canaryprism.slavacord.CommandHandler;
import canaryprism.slavacord.Commands;
import canaryprism.slavacord.CustomChoiceName;
import canaryprism.slavacord.annotations.*;
import canaryprism.slavacord.annotations.optionbounds.LongBounds;
import canaryprism.slavacord.annotations.optionbounds.StringLengthBounds;
import canaryprism.slavacord.autocomplete.AutocompleteSuggestion;
import canaryprism.slavacord.autocomplete.annotations.Autocompleter;
import canaryprism.slavacord.autocomplete.annotations.Autocompletes;
import canaryprism.slavacord.autocomplete.annotations.SearchSuggestions;
import canaryprism.slavacord.autocomplete.filteroptions.MatchStart;
import canaryprism.timebot.data.BirthdayData;
import canaryprism.timebot.data.BotData;
import canaryprism.timebot.data.ServerData;
import canaryprism.timebot.data.UserData;
import canaryprism.timebot.data.timers.AlarmData;
import canaryprism.timebot.data.timers.TimerData;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.RegularServerChannel;
import org.javacord.api.entity.channel.TextableRegularServerChannel;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.mention.AllowedMentions;
import org.javacord.api.entity.message.mention.AllowedMentionsBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.interaction.DiscordLocale;
import org.javacord.api.interaction.SlashCommandInteraction;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Bot {
    
    private static final Logger logger = LogManager.getLogger(Bot.class);
    
    private static final DateTimeFormatter DEFAULT_FORMATTER = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
            .append(DateTimeFormatter.ofPattern("[ (zzz)]"))
            .toFormatter();
    private static final AllowedMentions NO_MENTIONS = new AllowedMentionsBuilder()
            .setMentionRoles(false)
            .setMentionUsers(false)
            .setMentionEveryoneAndHere(false)
            .setMentionRepliedUser(false)
            .build();
    
    private static final DateTimeFormatter DEFAULT_FORMATTER_NO_YEAR = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss (zzz)");
    
    private final DiscordApi api;
    private final Path save_file;
    private final CommandHandler command_handler;
    private final BotData bot_data;
    
    private final Timer timer = new Timer("birthday_timer");
    
    public Bot(DiscordApi api, Path save_file) {
        this.api = api;
        this.save_file = save_file;
        
        this.command_handler = new CommandHandler(api);
        
        if (Files.isRegularFile(save_file)) {
            logger.info("save file found");
            try {
                var str = Files.readString(save_file);
                var json = new JSONObject(str);
                this.bot_data = new BotData(json, api);
            } catch (IOException e) {
                throw new RuntimeException(String.format("couldn't read save file '%s'!", save_file), e);
            }
        } else {
            logger.info("no save file found");
            this.bot_data = new BotData();
        }
    }
    
    public void save() {
        try {
            logger.debug("saving...");
            Files.writeString(save_file, bot_data.toJSON().toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.debug("saved");
        } catch (IOException e) {
            logger.error("failed to save data: ", e);
            logger.error("""
                    with json data:
                    {}
                    to file '{}'
                    """, bot_data.toJSON().toString(4), save_file);
        }
    }
    
    public void saveAsync() {
        Thread.ofVirtual().start(this::save);
    }
    
    public void start() {
        logger.info("Starting Bot");
        
        
        command_handler.register(new BotCommands(), true);
        
        
        refreshBirthdayTimers();
        refreshTimers();
        refreshAlarms();
        
        logger.info("Bot started normally");
    }
    
    abstract class AbstractTimerTask extends TimerTask {
        protected final Instant target_time;
        
        AbstractTimerTask(Instant target_time) {
            this.target_time = target_time;
        }
        
        public synchronized final void schedule() {
            timer.schedule(this, Date.from(target_time));
        }
        
        public abstract void update();
        
    }
    
    class BirthdayTask extends AbstractTimerTask {
        
        private final UserData data;
        
        BirthdayTask(UserData data) {
            super(data.getBirthdayData().orElseThrow().getNextBirthday());
            this.data = data;
        }
        
        @Override
        public synchronized void run() {
            var birthday = data.getBirthdayData().orElseThrow();
            
            var channel = birthday.getChannel();
            
            if (birthday.getAge().isPresent()) {
                channel.sendMessage(String.format("Today is %s's birthday! They're now %s years old! Happy birthday!", data.getUser().getMentionTag(), birthday.getAge().get()));
            } else {
                channel.sendMessage(String.format("Today is %s's birthday! Happy birthday!", data.getUser().getMentionTag()));
            }
            
            birthday.birthdayNotified();
            this.update();
            
            saveAsync();
        }
        
        @Override
        public synchronized void update() {
            logger.trace("updating timer for user {}", data.getUser());
            synchronized (data) {
                if (!data.getBirthdayData().map(BirthdayData::getNextBirthday).map(target_time::equals).orElse(false)) {
                    var opt_birthday = data.getBirthdayData();
                    if (opt_birthday.isPresent()) {
                        logger.trace("time changed, rescheduling to {}...",
                                () -> data.getBirthdayData().map(BirthdayData::getNextBirthday));
                        
                        this.cancel();
                        
                        var new_task = new BirthdayTask(data);
                        
                        synchronized (birthday_tasks) {
                            birthday_tasks.remove(this);
                            birthday_tasks.add(new_task);
                        }
                        
                        new_task.schedule();
                    } else {
                        logger.trace("birthday removed, cancelling task");
                        
                        this.cancel();
                        
                        synchronized (birthday_tasks) {
                            birthday_tasks.remove(this);
                        }
                    }
                }
            }
        }
        
    }
    
    private final Set<BirthdayTask> birthday_tasks = new HashSet<>();
    
    private void refreshBirthdayTimersAsync() {
        Thread.ofVirtual().start(this::refreshBirthdayTimers);
    }
    
    private void refreshBirthdayTimers() {
        logger.debug("refreshing birthday timers");
        
        Set<UserData> birthday_users;
        synchronized (birthday_tasks) {
            birthday_users = birthday_tasks.stream()
                    .map((e) -> e.data)
                    .collect(Collectors.toUnmodifiableSet());
        }
        
        bot_data.getServers()
                .stream()
                .flatMap((e) -> e.getUsers().stream())
                .filter((e) -> e.getBirthdayData().isPresent())
                .filter((e) -> !birthday_users.contains(e))
                .forEach((e) -> {
                    var task = new BirthdayTask(e);
                    
                    logger.debug("new birthday timer for user {}", e.getUser());
                    
                    synchronized (birthday_tasks) {
                        birthday_tasks.add(task);
                    }
                    
                    task.schedule();
                });
        
        synchronized (birthday_tasks) {
            for (var e : birthday_tasks)
                e.update();
        }
    }
    
    class TimerTimerTask extends AbstractTimerTask {
        
        private final TimerData data;
        
        TimerTimerTask(TimerData data) {
            super(data.getTargetTime());
            
            this.data = data;
        }
        
        @Override
        public void run() {
            if (!data.isActive())
                throw new IllegalStateException("User doesn't have this timer anymore without updating");
            
            data.getChannel().sendMessage(data.getMessage()).join();
            
            data.complete();
            
            this.update();
            
            saveAsync();
        }
        
        @Override
        public void update() {
            if (!data.isActive()) {
                logger.trace("timer timer cancelled, user dosn't have timer");
                
                this.cancel();
                
                synchronized (timer_timer_tasks) {
                    timer_timer_tasks.remove(this);
                }
            }
        }
    }
    
    private final Set<TimerTimerTask> timer_timer_tasks = new HashSet<>();
    
    private void refreshTimersAsync() {
        Thread.ofVirtual().start(this::refreshTimers);
    }
    
    private void refreshTimers() {
        logger.debug("refreshing timers");
        
        Set<TimerData> timers;
        synchronized (timer_timer_tasks) {
            timers = timer_timer_tasks.stream()
                    .map((e) -> e.data)
                    .collect(Collectors.toUnmodifiableSet());
        }
        
        bot_data.getServers()
                .stream()
                .flatMap((e) -> e.getUsers().stream())
                .flatMap((e) -> e.getTimers().stream())
                .filter((e) -> !timers.contains(e))
                .forEach((timer) -> {
                    var task = new TimerTimerTask(timer);
                    
                    logger.debug("new timer timer for user {}", timer.getOwner());
                    
                    synchronized (timer_timer_tasks) {
                        timer_timer_tasks.add(task);
                    }
                    
                    task.schedule();
                    
                });
        
        synchronized (timer_timer_tasks) {
            for (var e : timer_timer_tasks)
                e.update();
        }
    }
    
    class AlarmTimerTask extends AbstractTimerTask {
        
        private final AlarmData data;
        
        AlarmTimerTask(AlarmData data) {
            super(data.getTargetTime().orElseThrow());
            
            this.data = data;
        }
        
        @Override
        public void run() {
            if (!data.isActive())
                throw new IllegalStateException("User doesn't have this alarm anymore without updating");
            
            data.getChannel().sendMessage(data.getMessage()).join();
            
            data.complete();
            
            this.update();
            
            saveAsync();
        }
        
        @Override
        public void update() {
            if (!data.isActive() || data.getTargetTime().isEmpty()) {
                logger.trace("alarm cancelled, user dosn't have alarm");
                
                this.cancel();
                
                synchronized (alarm_tasks) {
                    alarm_tasks.remove(this);
                }
            } else if (!data.getTargetTime().get().equals(target_time)) {
                logger.trace("alarm changed, rescheduling");
                
                this.cancel();
                
                var new_task = new AlarmTimerTask(data);
                
                synchronized (alarm_tasks) {
                    alarm_tasks.remove(this);
                    alarm_tasks.add(new_task);
                }
                
                new_task.schedule();
            }
        }
    }
    
    private final Set<AlarmTimerTask> alarm_tasks = new HashSet<>();
    
    private void refreshAlarmsAsync() {
        Thread.ofVirtual().start(this::refreshAlarms);
    }
    
    private void refreshAlarms() {
        logger.debug("refreshing alarms");
        
        Set<AlarmData> alarms;
        synchronized (alarm_tasks) {
            alarms = alarm_tasks.stream()
                    .map((e) -> e.data)
                    .collect(Collectors.toUnmodifiableSet());
        }
        
        bot_data.getServers()
                .stream()
                .flatMap((e) -> e.getUsers().stream())
                .flatMap((e) -> e.getAlarms().stream())
                .filter((e) -> !alarms.contains(e))
                .forEach((timer) -> {
                    var task = new AlarmTimerTask(timer);
                    
                    logger.debug("new timer timer for user {}", timer.getOwner());
                    
                    synchronized (alarm_tasks) {
                        alarm_tasks.add(task);
                    }
                    
                    task.schedule();
                    
                });
        
        synchronized (alarm_tasks) {
            for (var e : alarm_tasks)
                e.update();
        }
    }
    
    enum ResponderFlags implements CustomChoiceName {
        EPHEMERAL("ephemeral", MessageFlag.EPHEMERAL),
        SILENT("silent", MessageFlag.SUPPRESS_NOTIFICATIONS);
        
        private final String choice_name;
        private final MessageFlag message_flag;
        
        
        ResponderFlags(String choiceName, MessageFlag messageFlag) {
            choice_name = choiceName;
            message_flag = messageFlag;
        }
        
        @Override
        public String getCustomName() {
            return choice_name;
        }
        
        public MessageFlag getMessageFlag() {
            return message_flag;
        }
    }
    
    public static Locale convertLocale(DiscordLocale locale) {
        return Locale.forLanguageTag(locale.getLocaleCode());
    }
    
    public static String formatDuration(Duration duration) {
        if (duration.isNegative())
            return "negative time";

        class Regexes {
            static final Pattern PLURAL_DAYS = Pattern.compile("\\b1 days\\b");
            static final Pattern PLURAL_HOURS = Pattern.compile("\\b1 hours\\b");
            static final Pattern PLURAL_MINUTES = Pattern.compile("\\b1 minutes\\b");
            static final Pattern PLURAL_SECONDS = Pattern.compile("\\b1 seconds\\b");
        }
        var str = DurationFormatUtils.formatDuration(duration.toMillis(),
                "[d 'days' ][H 'hours' ]m 'minutes' s 'seconds'");
        
        str = Regexes.PLURAL_DAYS.matcher(str).replaceAll("1 day");
        str = Regexes.PLURAL_HOURS.matcher(str).replaceAll("1 hour");
        str = Regexes.PLURAL_MINUTES.matcher(str).replaceAll("1 minute");
        str = Regexes.PLURAL_SECONDS.matcher(str).replaceAll("1 second");
        
        return str;
    }

    enum DayOfWeekOption implements CustomChoiceName {
        MONDAY("Monday", EnumSet.of(DayOfWeek.MONDAY)),
        TUESDAY("Tuesday", EnumSet.of(DayOfWeek.TUESDAY)),
        WEDNESDAY("Wednesday", EnumSet.of(DayOfWeek.WEDNESDAY)),
        THURSDAY("Thursday", EnumSet.of(DayOfWeek.THURSDAY)),
        FRIDAY("Friday", EnumSet.of(DayOfWeek.FRIDAY)),
        SATURDAY("Saturday", EnumSet.of(DayOfWeek.SATURDAY)),
        SUNDAY("Sunday", EnumSet.of(DayOfWeek.SUNDAY)),
        EVERY_DAY("Everyday", EnumSet.allOf(DayOfWeek.class));


        private final String choice_name;
        private final Set<DayOfWeek> day_of_week_set;

        DayOfWeekOption(String choice_name, EnumSet<DayOfWeek> day_of_week_set) {
            this.choice_name = choice_name;
            this.day_of_week_set = Collections.unmodifiableSet(day_of_week_set);
        }

        @Override
        public String getCustomName() {
            return choice_name;
        }
    }

    private boolean canCustomMessages(User user, Server server) {
        if (server.getAllowedPermissions(user).contains(PermissionType.MANAGE_MESSAGES))
            return true;

        return bot_data.getServerData(server)
                .flatMap(ServerData::allowsCustomMessages)
                .orElse(false);
    }
    
    @CreateGlobal
    class BotCommands implements Commands {
        @Command(name = "ping", description = "Pong !")
        @ReturnsResponse(ephemeral = true)
        String ping() {
            logger.trace("/ping command");
            return "Pong !";
        }
        
        @Command(name = "time", description = "get a user's time (the user needs to have a timezone set)", enabledInDMs = false)
        @ReturnsResponse(ephemeral = true)
        String time(
                @Interaction SlashCommandInteraction interaction,
                
                @Option(name = "of", description = "the user to get the time of (default: yourself)")
                Optional<User> opt_user,
                
                @Option(name = "message_flag", description = "the message flags to use (default: none)")
                Optional<ResponderFlags> flag
        ) {
            var now = Instant.now();
            
            var user = opt_user.orElse(interaction.getUser());
            
            var server = interaction.getServer().orElseThrow();
            
            logger.trace("/time command; user: {}, server: {}, target: {}", interaction.getUser(), server, user);
            
            var opt_timezone = bot_data.getServerData(server)
                    .flatMap((e) -> e.getUserData(user))
                    .flatMap(UserData::getTimezone);
            
            if (opt_timezone.isEmpty())
                return String.format("Error: User %s has no timezone set! Set a timezone with `/timezone set`", user.getMentionTag());
            
            var timezone = opt_timezone.get();
            
            var timezone_visible = bot_data.getServerData(server)
                    .flatMap((e) -> e.getUserData(user))
                    .flatMap(UserData::isTimezoneVisible)
                    .orElse(true);
            
            var locale = bot_data.getServerData(server)
                    .flatMap((e) -> e.getUserData(interaction.getUser()))
                    .flatMap(UserData::getLocale)
                    .orElse(convertLocale(interaction.getLocale()));
            
            
            var formatter = bot_data.getServerData(server)
                    .flatMap((e) -> e.getUserData(interaction.getUser()))
                    .flatMap(UserData::getFormatter)
                    .orElse(DEFAULT_FORMATTER)
                    .withLocale(locale);
            
            String time;
            if (timezone_visible) {
                time = now.atZone(timezone).format(formatter);
            } else {
                time = now.atZone(timezone).toLocalDateTime().format(formatter);
            }
            
            var message = String.format("The current time for %s is %s",
                    user.getMentionTag(), time);
            
            var responder = interaction.createImmediateResponder()
                    .setAllowedMentions(NO_MENTIONS)
                    .setContent(message);
            
            interaction.getServer()
                    .flatMap(bot_data::getServerData)
                    .flatMap(ServerData::getForcedMessageFlag)
                    .or(() -> flag.map(ResponderFlags::getMessageFlag))
                    .ifPresent(responder::setFlags);
            
            responder.respond();
            
            return null;
        }
        
        @CommandGroup(name = "timezone", enabledInDMs = false)
        class Timezone {
            @Command(name = "set", description = "set timezone ZoneId")
            @ReturnsResponse(ephemeral = true)
            String set(
                    @Interaction
                    SlashCommandInteraction interaction,
                    
                    @Autocompletes(autocompleter = "getTimezones")
                    @StringLengthBounds(min = 1)
                    @Option(name = "timezone", description = "the timezone to set to")
                    String timezone_query
            ) {
                var server = interaction.getServer().orElseThrow();
                
                logger.trace("/timezone set command; user: {}, server: {}, timezone_query: {}", interaction.getUser(), server, timezone_query);
                
                try {
                    var timezone = ZoneId.of(timezone_query);
                    
                    var data = bot_data.obtainServerData(server).obtainUserData(interaction.getUser());
                    
                    data.setTimezone(timezone);
                    
                    saveAsync();
                    
                    return String.format("""
                            Set timezone to %s in this server
                            Preview: %s
                            """, timezone, previewTime(interaction));
                } catch (DateTimeException e) {
                    return String.format("Error: Timezone of id '%s' not found", timezone_query);
                }
            }
            
            @SearchSuggestions(at = MatchStart.ANYWHERE, ignorePunctuation = true)
            @Autocompleter
            List<AutocompleteSuggestion<String>> getTimezones(String input) {
                logger.trace("timezone autocomplete for input '{}'", input);
                var list = new ArrayList<AutocompleteSuggestion<String>>();
                
                if (!input.isBlank()) {
                    try {
                        //noinspection ResultOfMethodCallIgnored
                        ZoneId.of(input);
                        list.add(AutocompleteSuggestion.of(
                                String.format("valid timezone: %s", input),
                                input
                        ));
                    } catch (DateTimeException e) {
                        list.add(AutocompleteSuggestion.of(
                                String.format("unrecognised timezone: %s", input),
                                input
                        ));
                    }
                }
                
                list.addAll(ZoneId.SHORT_IDS
                        .entrySet()
                        .stream()
                        .map((e) -> AutocompleteSuggestion.of(
                                String.format("%s (%s)", e.getKey(), e.getValue()),
                                e.getValue()
                        ))
                        .toList());
                
                list.addAll(ZoneId.getAvailableZoneIds()
                        .stream()
                        .map(AutocompleteSuggestion::of)
                        .toList());
                
                return list;
            }

            @Command(name = "get", description = "get your current set timezone")
            @ReturnsResponse(ephemeral = true)
            String get(@Interaction SlashCommandInteraction interaction) {
                var server = interaction.getServer().orElseThrow();
                var user = interaction.getUser();
                logger.trace("/timezone get command; user: {}, server: {}", user, server);

                return bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(user))
                        .flatMap(UserData::getTimezone)
                        .map((e) -> String.format("Your current set timezone is %s", e))
                        .orElse("Error: You don't have a timezone set! you can set one with `/timezone set`");
            }
            
            @Command(name = "setvisible", description = "set whether your timezone is visible to others")
            @ReturnsResponse(ephemeral = true)
            String setvisible(
                    @Interaction
                    SlashCommandInteraction interaction,
                    
                    @Option(name = "visible", description = "whether your timezone is visible to others")
                    boolean state
            ) {
                var server = interaction.getServer().orElseThrow();
                var user = interaction.getUser();
                
                logger.trace("/timezone setvisible command; user: {}, server: {}", user, server);
                
                try {

                    var current_state = bot_data.getServerData(server)
                            .flatMap((e) -> e.getUserData(user))
                            .flatMap(UserData::isTimezoneVisible)
                            .orElse(true);
                    
                    if (current_state == state)
                        return String.format("Error: Your timezone visibility is already set to %s", current_state);
                    
                    var data = bot_data.obtainServerData(server).obtainUserData(interaction.getUser());
                    
                    data.setTimezoneVisible(state);
                    
                    saveAsync();
                    
                    return String.format("""
                            Set timezone visibility to be %s in this server
                            Preview: %s
                            """, state, previewTime(interaction));
                } catch (DateTimeException e) {
                    return String.format("Error: Timezone of id '%s' not found", state);
                }
            }
            
            @Command(name = "remove", description = "remove timezone information from the bot")
            @ReturnsResponse(ephemeral = true)
            String remove(@Interaction SlashCommandInteraction interaction) {
                var server = interaction.getServer().orElseThrow();
                var user = interaction.getUser();
                
                logger.trace("/timezone remove command; user: {}, server: {}", user, server);
                
                var data = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(user));
                
                if (data.flatMap(UserData::getTimezone).isEmpty())
                    return "Error: You don't have timezone information in this server already";
                
                data.get().setTimezone(null);
                
                saveAsync();
                
                return "Removed your timezone information for this server";
            }
            
        }
        
        @Command(name = "setformatter", description = "set the DateTimeFormatter to use when calling /time", enabledInDMs = false)
        @ReturnsResponse(ephemeral = true)
        String setformatter(
                @Interaction SlashCommandInteraction interaction,
                @Option(name = "pattern", description = "the pattern to set to, if not present resets it to default") Optional<String> opt_pattern
        ) {
            var server = interaction.getServer().orElseThrow();
            
            logger.trace("/time command; user: {}, server: {}, pattern: {}", interaction.getUser(), server, opt_pattern);
            
            if (opt_pattern.isPresent()) {
                var pattern = opt_pattern.get();
                try {
                    var formatter = DateTimeFormatter.ofPattern(pattern);
                    
                    formatter.format(LocalDateTime.now());
                    
                    bot_data.obtainServerData(server)
                            .obtainUserData(interaction.getUser())
                            .setFormatter(pattern);
                    
                    saveAsync();
                    
                    return String.format("""
                            Set formatter to pattern `%s`
                            Preview: %s
                            """, pattern, previewTime(interaction));

                } catch (IllegalArgumentException e) {
                    return String.format("Error: Invalid DateTimeFormat pattern: `%s`", pattern);
                } catch (DateTimeException e) {
                    return "Error: Pattern may only request timezone information in square brackets (to indicate optionalness)";
                }
            } else {
                var opt_data = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(interaction.getUser()));
                
                if (opt_data.flatMap(UserData::getFormatter).isEmpty())
                    return "Error: No operation performed";
                
                @SuppressWarnings("OptionalGetWithoutIsPresent")
                var old_formatter = opt_data.get().getFormatterPattern().get();
                
                opt_data.get().setFormatter(null);
                
                saveAsync();
                
                return String.format("""
                        Reset formatter to default from `%s`
                        Preview: %s
                        """, old_formatter, previewTime(interaction));
            }
        }
        
        @Command(name = "setlocale", description = "set the Locale to use when calling /time", enabledInDMs = false)
        @ReturnsResponse(ephemeral = true)
        String setlocale(
                @Interaction SlashCommandInteraction interaction,
                
                @Autocompletes(autocompleter = "getLocales")
                @StringLengthBounds(min = 1)
                @Option(name = "language_tag", description = "the pattern to set to, if not present resets it to default") Optional<String> opt_language_tag
        ) {
            var server = interaction.getServer().orElseThrow();
            logger.trace("/setlocale command; user: {}, server: {}", interaction.getUser(), server);
            
            if (opt_language_tag.isPresent()) {
                
                var language_tag = opt_language_tag.get();
                var locale = Locale.forLanguageTag(language_tag);
                
                if (LocaleUtils.isLanguageUndetermined(locale))
                    return String.format("Error: Unrecognised language tag `%s`", language_tag);
                
                bot_data.obtainServerData(server)
                        .obtainUserData(interaction.getUser())
                        .setLocale(locale);
                
                saveAsync();
                
                return String.format("""
                        Set locale to `%s`
                        Preview: %s
                        """, locale, previewTime(interaction));
            } else {
                var opt_data = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(interaction.getUser()));
                
                if (opt_data.flatMap(UserData::getLocale).isEmpty())
                    return "Error: No operation performed";
                
                @SuppressWarnings("OptionalGetWithoutIsPresent")
                var old_locale = opt_data.get().getLocale().get();
                
                opt_data.get().setLocale(null);
                
                saveAsync();
                
                return String.format("""
                        Reset locale to default from `%s`
                        Preview: %s
                        """, old_locale, previewTime(interaction));
            }
        }
        
        @SearchSuggestions(at = MatchStart.ANYWHERE, ignorePunctuation = true)
        @Autocompleter
        List<AutocompleteSuggestion<String>> getLocales(String input) {
            logger.trace("autocompleting locale for input '{}'", input);
            var list = new ArrayList<AutocompleteSuggestion<String>>();
            
            if (!input.isBlank()) {
                if (!LocaleUtils.isLanguageUndetermined(Locale.forLanguageTag(input))) {
                    list.add(AutocompleteSuggestion.of(
                            String.format("valid locale: %s", input),
                            input
                    ));
                } else {
                    list.add(AutocompleteSuggestion.of(
                            String.format("unrecognised locale: %s", input),
                            input
                    ));
                }
            }
            
            
            list.addAll(Locale.availableLocales()
                    .filter((e) -> !LocaleUtils.isLanguageUndetermined(e))
                    .map((e) -> AutocompleteSuggestion.of(
                            String.format("%s (%s)", e, e.toLanguageTag()),
                            e.toLanguageTag()
                    ))
                    .toList());
            
            return list;
        }
        
        private String previewTime(SlashCommandInteraction interaction) {
            var now = Instant.now();
            
            var server = interaction.getServer().orElseThrow();
            
            var data = bot_data.getServerData(server)
                    .flatMap((e) -> e.getUserData(interaction.getUser()));
            
            var timezone = data.flatMap(UserData::getTimezone).orElse(ZoneOffset.UTC);
            
            var timezone_visible = data.flatMap(UserData::isTimezoneVisible).orElse(true);
            
            var locale = data.flatMap(UserData::getLocale)
                    .orElse(convertLocale(interaction.getLocale()));
            
            var formatter = data.flatMap(UserData::getFormatter)
                    .orElse(DEFAULT_FORMATTER)
                    .withLocale(locale);
            
            TemporalAccessor time;
            if (timezone_visible) {
                time = now.atZone(timezone);
            } else {
                time = now.atZone(timezone).toLocalDateTime();
            }

            return formatter.format(time);
        }
        
        @RequiresPermissions(PermissionType.MANAGE_MESSAGES)
        @CommandGroup(name = "moderation", enabledInDMs = false)
        class Moderation {
            
            @CommandGroup(name = "forcemessageflag")
            class ForceMessageFlag {
                @Command(name = "set", description = "Force /time responses to use a specific message flag")
                @ReturnsResponse(ephemeral = true)
                String set(
                        @Interaction SlashCommandInteraction interaction,
                        @Option(name = "flag", description = "the flag to set, if empty removes the forced message flag") Optional<ResponderFlags> opt_flag
                ) {
                    var server = interaction.getServer().orElseThrow();
                    
                    logger.trace("/moderation forcemessageflag set command; user: {}, server: {}, flag: {}", interaction.getUser(), server, opt_flag);
                    
                    var data = bot_data.obtainServerData(server);
                    
                    data.forceMessageFlag(opt_flag.map(ResponderFlags::getMessageFlag).orElse(null));
                    
                    saveAsync();
                    
                    return opt_flag.map(responderFlags ->
                                    String.format("Set server to force %s messages", responderFlags.name()))
                            .orElse("Set server to not force any message flags");
                }
                
                @Command(name = "get", description = "get forced message flag for /time responses")
                @ReturnsResponse(ephemeral = true)
                String get(@Interaction SlashCommandInteraction interaction) {
                    var server = interaction.getServer().orElseThrow();
                    logger.trace("/moderation forcemessageflag get command; user: {}, server: {}", interaction.getUser(), server);
                    
                    return bot_data.getServerData(server)
                            .flatMap(ServerData::getForcedMessageFlag)
                            .map((e) ->
                                    String.format("Server currently forces %s messages", e))
                            .orElse("Server currently doesn't force any message flags");
                }
            }
            
            @CommandGroup(name = "birthdaychannel")
            class BirthdayChannel {
                @Command(name = "add", description = "add allowed birthday channel")
                @ReturnsResponse(ephemeral = true)
                String add(
                        @Interaction SlashCommandInteraction interaction,
                        @Option(name = "channel", description = "the channel to allow") RegularServerChannel channel
                ) {
                    var server = interaction.getServer().orElseThrow();
                    logger.trace("/moderation birthdaychannel add command; user: {}, server: {}, channel: {}", interaction.getUser(), server, channel);
                    
                    if (!(channel instanceof TextableRegularServerChannel text_channel))
                        return "Error: Not a textable channel";
                    
                    if (bot_data.getServerData(server)
                            .map(ServerData::getAllowedBirthdayChannels)
                            .map((e) -> e.contains(text_channel))
                            .orElse(false))
                        return String.format("Error: %s already an allowed birthday channel", text_channel.getMentionTag());
                    
                    var data = bot_data.obtainServerData(server);
                    
                    data.addAllowedBirthdayChannel(text_channel);
                    
                    saveAsync();
                    
                    return String.format("Added %s to allowed birthday notification channels", text_channel.getMentionTag());
                }
                
                @Command(name = "remove", description = "remove allowed birthday channel")
                @ReturnsResponse(ephemeral = true)
                String remove(
                        @Interaction SlashCommandInteraction interaction,
                        @Option(name = "target_channel", description = "the channel to disallow") RegularServerChannel target_channel,
                        @Option(name = "fallback_channel", description = "the channel to set birthdays targeting target_channel to change to") RegularServerChannel fallback_channel
                ) {
                    var server = interaction.getServer().orElseThrow();
                    logger.trace("/moderation birthdaychannel remove command; user: {}, server: {}, target_channel: {}, fallback_channel: {}", interaction.getUser(), server, target_channel, fallback_channel);
                    
                    if (!(target_channel instanceof TextableRegularServerChannel target_text_channel))
                        return String.format("Error: <#%s> not a textable channel", target_channel.getIdAsString());
                    
                    if (!(fallback_channel instanceof TextableRegularServerChannel fallback_text_channel))
                        return String.format("Error: <#%s> not a textable channel", fallback_channel.getIdAsString());
                    
                    var data = bot_data.getServerData(server);
                    
                    if (!data.map(ServerData::getAllowedBirthdayChannels)
                            .map((e) -> e.contains(target_text_channel))
                            .orElse(false))
                        return String.format("Error: %s isn't an allowed birthday channel", target_text_channel.getMentionTag());
                    
                    if (!data.map(ServerData::getAllowedBirthdayChannels)
                            .map((e) -> e.contains(fallback_text_channel))
                            .orElse(false))
                        return String.format("Error: %s isn't an allowed birthday channel", fallback_text_channel.getMentionTag());
                    
                    data.get().removeAllowedBirthdayChannel(target_text_channel);
                    
                    data.map(ServerData::getUsers)
                            .stream()
                            .flatMap(Set::stream)
                            .map(UserData::getBirthdayData)
                            .flatMap(Optional::stream)
                            .filter((e) -> e.getChannel().equals(target_text_channel))
                            .forEach((e) -> e.setChannel(fallback_text_channel));
                    
                    saveAsync();
                    
                    return String.format("Removed %s from allowed birthday channels", target_text_channel.getMentionTag());
                }
            }

            @CommandGroup(name = "custommessage")
            class CustomMessage {
                @Command(name = "enable", description = "enable custom messages in this server for all users")
                @ReturnsResponse(ephemeral = true)
                String enable(@Interaction SlashCommandInteraction interaction) {
                    var server = interaction.getServer().orElseThrow();
                    logger.trace("/moderation custommessage enable command; user: {}, server: {}", interaction.getUser(), server);

                    var state = bot_data.getServerData(server)
                            .flatMap(ServerData::allowsCustomMessages)
                            .orElse(false);

                    if (state)
                        return "Error: Server already allows custom messages";

                    bot_data.obtainServerData(server)
                            .setAllowsCustomMessages(true);

                    saveAsync();

                    return "Set server to allow custom messages for all users";
                }
                @Command(name = "disable", description = "disable custom messages in this server for all users")
                @ReturnsResponse(ephemeral = true)
                String disable(@Interaction SlashCommandInteraction interaction) {
                    var server = interaction.getServer().orElseThrow();
                    logger.trace("/moderation custommessage disable command; user: {}, server: {}", interaction.getUser(), server);

                    var state = bot_data.getServerData(server)
                            .flatMap(ServerData::allowsCustomMessages)
                            .orElse(false);

                    if (!state)
                        return "Error: Server already disallows custom messages";

                    bot_data.obtainServerData(server)
                            .setAllowsCustomMessages(false);

                    saveAsync();

                    return "Set server to disallow custom messages for all users";
                }
            }
        }
        
        @CommandGroup(name = "birthday", enabledInDMs = false)
        class Birthday {
            
            @Command(name = "set", description = "set your birthday to send a message for")
            @ReturnsResponse(ephemeral = true)
            String set(
                    @Interaction SlashCommandInteraction interaction,
                    
                    @Option(name = "channel", description = "channel to send message to") RegularServerChannel channel,
                    
                    @LongBounds(min = 1, max = 31)
                    @Option(name = "day") long long_day,
                    
                    @LongBounds(min = 1, max = 12)
                    @Option(name = "month") long long_month,
                    
                    @LongBounds(min = 0)
                    @Option(name = "year", description = "birth year (to track age, optional)") Optional<Long> long_year
            ) {
                var server = interaction.getServer().orElseThrow();
                logger.trace("/birthday set command; user: {}, server: {}", interaction.getUser(), server);
                
                if (!(channel instanceof TextableRegularServerChannel text_channel))
                    return String.format("Error: <#%s> not a textable channel", channel.getIdAsString());
                
                var allowed_channels = bot_data.getServerData(server)
                        .map(ServerData::getAllowedBirthdayChannels);
                
                if (!allowed_channels.map((e) -> e.contains(channel)).orElse(false)) {
                    if (allowed_channels.map(Set::isEmpty).orElse(true))
                        return """
                                Error: This server doesn't allow any birthday notifications
                                if you're a moderator you can add one with `/moderation birthdaychannel add`
                                """;
                    else
                        return String.format("""
                                Error: %s is not in the allowed birthday notification channels
                                allowed channels: %s
                                """,
                                text_channel.getMentionTag(),
                                allowed_channels.map((set) ->
                                        set.stream()
                                                .map((e) -> "\n" + e.getMentionTag())
                                                .reduce("", (a, b) -> a + b)
                                ).orElse("")
                        );
                }
                
                
                var timezone = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(interaction.getUser()))
                        .flatMap(UserData::getTimezone)
                        .orElse(ZoneOffset.UTC);
                int year = long_year.orElse(-1L).intValue(), month = ((int) long_month), day = ((int) long_day);
                
                try {
                    var birthday = ZonedDateTime.of(year, month, day,
                            0, 0, 0, 0, timezone);
                    
                    // we hate people born on this stupid date
                    //noinspection MagicNumber
                    if (birthday.getMonthValue() == 2 && birthday.getDayOfMonth() == 29)
                        throw new DateTimeException("Febrary 29th not allowed");
                    
                    var data = bot_data.obtainServerData(server)
                            .obtainUserData(interaction.getUser());
                    
                    var birthday_data = new BirthdayData(birthday, text_channel);
                    
                    data.setBirthdayData(birthday_data);
                    
                    DateTimeFormatter formatter;
                    if (long_year.isPresent()) {
                        formatter = DEFAULT_FORMATTER;
                    } else {
                        formatter = DEFAULT_FORMATTER_NO_YEAR;
                    }
                    
                    var locale = data.getLocale().orElse(convertLocale(interaction.getLocale()));
                    
                    formatter = formatter.withLocale(locale);
                    
                    refreshBirthdayTimersAsync();
                    
                    saveAsync();
                    
                    return String.format("Set birthday notification to %s", formatter.format(birthday));
                } catch (DateTimeException e) {
                    return String.format("Error: Invalid date: %s", e.getMessage());
                }
            }
            
            @Command(name = "remove", description = "remove birthday notification")
            @ReturnsResponse(ephemeral = true)
            String remove(@Interaction SlashCommandInteraction interaction) {
                var server = interaction.getServer().orElseThrow();
                var user = interaction.getUser();
                logger.trace("/birthday remove command; user: {}, server: {}", user, server);
                
                var data = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(user));
                
                if (data.flatMap(UserData::getBirthdayData).isEmpty())
                    return "Error: You don't have any birthday data";
                
                data.get().setBirthdayData(null);
                
                refreshBirthdayTimersAsync();
                
                saveAsync();
                
                return "Removed birthday data";
            }
        
        }
        
        @CommandGroup(name = "timer", enabledInDMs = false)
        class Timer {
            @Command(name = "list", description = "list your current active timers")
            @ReturnsResponse(ephemeral = true)
            String list(@Interaction SlashCommandInteraction interaction) {
                var server = interaction.getServer().orElseThrow();
                var user = interaction.getUser();
                logger.trace("/timer list command; user: {}, server: {}", user, server);
                
                var timers = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(user))
                        .map(UserData::getTimers)
                        .orElse(List.of());
                
                if (timers.isEmpty()) {
                    return "You have no active timers";
                }
                
                var now = Instant.now();
                
                var sb = new StringBuilder();
                
                sb.append("List of current timers:");
                
                for (int i = 0; i < timers.size(); i++) {
                    var e = timers.get(i);
                    sb.append('\n')
                            .append(i)
                            .append(": for ")
                            .append(formatDuration(e.getDuration()))
                            .append(" with ")
                            .append(formatDuration(Duration.between(now, e.getTargetTime())))
                            .append(" left in channel <#")
                            .append(e.getChannel().getIdAsString())
                            .append('>');
                }
                
                return sb.toString();
            }
            
            @Command(name = "new", description = "add a new timer")
            @ReturnsResponse(ephemeral = true)
            String $new(
                    @Interaction SlashCommandInteraction interaction,
                    @Option(name = "days") Optional<Long> opt_days,
                    @Option(name = "hours") Optional<Long> opt_hours,
                    @Option(name = "minutes") Optional<Long> opt_minutes,
                    @Option(name = "seconds") Optional<Long> opt_seconds,
                    @Option(name = "message", description = "custom message to send when time is up (optional)") Optional<String> opt_message
            ) {
                var server = interaction.getServer().orElseThrow();
                logger.trace("/timer new command; user: {}, server: {}", interaction.getUser(), server);

                if (!canCustomMessages(interaction.getUser(), server)) {
                    return """
                            You can't use custom messages in this server!
                            Moderators may enable all users to set custom messages with `/moderation custommessage enable`
                            """;
                }
                
                long
                        days = opt_days.orElse(0L),
                        hours = opt_hours.orElse(0L),
                        minutes = opt_minutes.orElse(0L),
                        seconds = opt_seconds.orElse(0L);
                
                var duration = Duration.ofDays(days)
                        .plusHours(hours)
                        .plusMinutes(minutes)
                        .plusSeconds(seconds);
                
                
                if (duration.isNegative())
                    return "Error: Time specified is negative";
                
                
                var user = bot_data.obtainServerData(server)
                        .obtainUserData(interaction.getUser());
                
                var timer = new TimerData(
                        user,
                        duration,
                        interaction.getChannel().orElseThrow(),
                        opt_message.orElse(String.format("Timer for %s ended %s",
                                formatDuration(duration), interaction.getUser().getMentionTag()))
                );
                
                user.addTimer(timer);
                
                refreshTimersAsync();
                
                saveAsync();
                
                return String.format("Added timer for %s", formatDuration(duration));
            }
            
            @Command(name = "cancel", description = "cancel a timer")
            @ReturnsResponse(ephemeral = true)
            String cancel(
                    @Interaction SlashCommandInteraction interaction,
                    @LongBounds(min = 0) @Option(name = "index", description = "the index of the timer to cancel") Long index
            ) {
                var server = interaction.getServer().orElseThrow();
                logger.trace("/timer cancel command; user: {}, server: {}, index: {}", interaction.getUser(), server, index);
                
                var opt_user = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(interaction.getUser()));
                var opt_timer = opt_user.flatMap((e) -> e.getTimer(index.intValue()));
                
                if (opt_timer.isEmpty())
                    return "Error: Timer with that index does not exist";
                
                var user = opt_user.get();
                var timer = opt_timer.get();
                
                user.removeTimer(timer);
                
                refreshTimersAsync();
                
                saveAsync();
                
                return String.format("Timer for %s cancelled", formatDuration(timer.getDuration()));
            }
        }
        
        
        @CommandGroup(name = "alarm", enabledInDMs = false)
        class Alarm {
            @Command(name = "list", description = "list your current active alarms")
            @ReturnsResponse(ephemeral = true)
            String list(@Interaction SlashCommandInteraction interaction) {
                var server = interaction.getServer().orElseThrow();
                var user = interaction.getUser();
                logger.trace("/alarm list command; user: {}, server: {}", user, server);
                
                var alarms = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(user))
                        .map(UserData::getAlarms)
                        .orElse(List.of());
                
                if (alarms.isEmpty()) {
                    return "You have no active alarms";
                }
                
                var sb = new StringBuilder();
                
                sb.append("List of current alarms:");
                
                for (int i = 0; i < alarms.size(); i++) {
                    var e = alarms.get(i);
                    sb.append('\n')
                            .append(i)
                            .append(": at ")
                            .append(e.getTime());
                    
                    var repeating = e.getRepeatingDays();
                    
                    if (!repeating.isEmpty())
                        sb.append(" repeating ")
                            .append(repeating);

                    sb.append(" in channel <#")
                            .append(e.getChannel().getIdAsString())
                            .append('>');
                }
                
                return sb.toString();
            }
            
            @Command(name = "new", description = "make a new alarm")
            @ReturnsResponse(ephemeral = true)
            String $new(
                    @Interaction SlashCommandInteraction interaction,
                    
                    @LongBounds(min = 0, max = 23)
                    @Option(name = "hour") Long hour,
                    
                    @LongBounds(min = 0, max = 59)
                    @Option(name = "minute") Long minute,
                    
                    @LongBounds(min = 0, max = 59)
                    @Option(name = "second") Optional<Long> opt_seconds,
                    
                    @Option(name = "message", description = "custom message to send (optional)") Optional<String> opt_message
            ) {
                var server = interaction.getServer().orElseThrow();
                logger.trace("/alarm new command; user: {}, server: {}", interaction.getUser(), server);

                if (!canCustomMessages(interaction.getUser(), server)) {
                    return """
                            You can't use custom messages in this server!
                            Moderators may enable all users to set custom messages with `/moderation custommessage enable`
                            """;
                }
                
                var opt_timezone = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(interaction.getUser()))
                        .flatMap(UserData::getTimezone);
                
                if (opt_timezone.isEmpty())
                    return "Error: You don't have a timezone set! you can set one with `/timezone set`";
                
                var user = bot_data.obtainServerData(server)
                        .obtainUserData(interaction.getUser());
                
                var time = LocalTime.of(hour.intValue(), minute.intValue(), opt_seconds.map(Long::intValue).orElse(0));
                
                var data = new AlarmData(
                        user,
                        time,
                        interaction.getChannel().orElseThrow(),
                        opt_message.orElse(String.format("Alarm for %s %s",
                                time.toString(), interaction.getUser().getMentionTag()))
                );
                
                user.addAlarm(data);
                
                refreshAlarmsAsync();
                
                saveAsync();
                //TODO: fix please
                return String.format("""
                        Added new alarm for %s
                        Alarms are nonrepeating by default, you can use `/alarm addrepeat` to add repeating days
                        """, time);
            }
            
            @Command(name = "modify", description = "modify an existing alarm")
            @ReturnsResponse(ephemeral = true)
            String modify(
                    @Interaction SlashCommandInteraction interaction,
                    
                    @LongBounds(min = 0)
                    @Option(name = "index") Long index,
                    
                    @LongBounds(min = 0, max = 23)
                    @Option(name = "hour") Optional<Long> opt_hour,
                    
                    @LongBounds(min = 0, max = 59)
                    @Option(name = "minute") Optional<Long> opt_minute,
                    
                    @LongBounds(min = 0, max = 59)
                    @Option(name = "second") Optional<Long> opt_seconds,
                    
                    @Option(name = "message") Optional<String> opt_message
            ) {
                var server = interaction.getServer().orElseThrow();
                var user = interaction.getUser();
                logger.trace("/alarm modify command; user: {}, server: {}", user, server);
                
                var opt_alarm = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(user))
                        .flatMap((e) -> e.getAlarm(index.intValue()));
                
                if (opt_alarm.isEmpty()) {
                    return "Error: Alarm of that index doesn't exist";
                }
                
                var alarm = opt_alarm.get();
                
                var time = alarm.getTime();
                
                if (opt_hour.isPresent())
                    time = time.withHour(opt_hour.get().intValue());
                
                if (opt_minute.isPresent())
                    time = time.withMinute(opt_minute.get().intValue());
                
                if (opt_seconds.isPresent())
                    time = time.withSecond(opt_seconds.get().intValue());
                
                alarm.setTime(time);
                
                opt_message.ifPresent(alarm::setMessage);

                refreshAlarmsAsync();

                saveAsync();
                
                return String.format("Modified alarm %s to be at %s with message '%s'",
                        index, time, opt_message.orElse(alarm.getMessage()));
            }
            
            @Command(name = "addrepeat", description = "add a day of week an alarm repeats on")
            @ReturnsResponse(ephemeral = true)
            String addrepeat(
                    @Interaction SlashCommandInteraction interaction,
                    @Option(name = "index") Long index,
                    @Option(name = "day") DayOfWeekOption day_of_week_option
            ) {
                var server = interaction.getServer().orElseThrow();
                var user = interaction.getUser();
                logger.trace("/alarm addrepeat command; user: {}, server: {}, day: {}", user, server, day_of_week_option);

                var opt_alarm = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(user))
                        .flatMap((e) -> e.getAlarm(index.intValue()));

                if (opt_alarm.isEmpty()) {
                    return "Error: Alarm of that index doesn't exist";
                }

                var alarm = opt_alarm.get();

                var success = day_of_week_option.day_of_week_set
                        .stream()
                        .map(alarm::addRepeatingDay)
                        .reduce(false, (a, b) -> a || b);

                if (success)
                    return String.format("Added %s to repeating days for alarm %s",
                            day_of_week_option.getCustomName(), index);
                else
                    return String.format("Error: Alarm %s already repeats %s",
                            index, day_of_week_option.getCustomName());
            }

            @Command(name = "removerepeat", description = "remove a day of week an alarm repeats on")
            @ReturnsResponse(ephemeral = true)
            String removerepeat(
                    @Interaction SlashCommandInteraction interaction,
                    @Option(name = "index") Long index,
                    @Option(name = "day") DayOfWeekOption day_of_week_option
            ) {
                var server = interaction.getServer().orElseThrow();
                var user = interaction.getUser();
                logger.trace("/alarm removerepeat command; user: {}, server: {}, day: {}", user, server, day_of_week_option);

                var opt_alarm = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(user))
                        .flatMap((e) -> e.getAlarm(index.intValue()));

                if (opt_alarm.isEmpty()) {
                    return "Error: Alarm of that index doesn't exist";
                }

                var alarm = opt_alarm.get();

                var success = day_of_week_option.day_of_week_set
                        .stream()
                        .map(alarm::removeRepeatingDay)
                        .reduce(false, (a, b) -> a || b);

                if (success)
                    return String.format("Removed %s from repeating days for alarm %s",
                            day_of_week_option.getCustomName(), index);
                else
                    return String.format("Error: Alarm %s already doesn't repeat %s",
                            index, day_of_week_option.getCustomName());
            }
        }
    }
}
