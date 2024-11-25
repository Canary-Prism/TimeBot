package canaryprism.timebot;

import canaryprism.slavacord.CommandHandler;
import canaryprism.slavacord.Commands;
import canaryprism.slavacord.CustomChoiceName;
import canaryprism.slavacord.annotations.*;
import canaryprism.slavacord.annotations.optionbounds.StringLengthBounds;
import canaryprism.slavacord.autocomplete.AutocompleteSuggestion;
import canaryprism.slavacord.autocomplete.annotations.Autocompleter;
import canaryprism.slavacord.autocomplete.annotations.Autocompletes;
import canaryprism.slavacord.autocomplete.annotations.SearchSuggestions;
import canaryprism.slavacord.autocomplete.filteroptions.MatchStart;
import canaryprism.timebot.data.BotData;
import canaryprism.timebot.data.ServerData;
import canaryprism.timebot.data.UserData;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.mention.AllowedMentions;
import org.javacord.api.entity.message.mention.AllowedMentionsBuilder;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
    
    private final DiscordApi api;
    private final Path save_file;
    private final CommandHandler command_handler;
    private final BotData bot_data;
    
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
            logger.info("saving...");
            Files.writeString(save_file, bot_data.toJSON().toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.info("saved");
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
        logger.info("Bot started normally");
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
    
    @CreateGlobal
    class BotCommands implements Commands {
        @Command(name = "ping", description = "Pong !")
        @ReturnsResponse(ephemeral = true)
        String ping() {
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
            
            var opt_timezone = bot_data.getServerData(server)
                    .flatMap((e) -> e.getUserData(user))
                    .flatMap(UserData::getTimezone);
            
            if (opt_timezone.isEmpty())
                return String.format("User %s has no timezone set!", user.getMentionTag());
            
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
                time = now.atZone(timezone).toLocalTime().format(formatter);
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
                    return String.format("Timezone of id '%s' not found", timezone_query);
                }
            }
            
            @SearchSuggestions(at = MatchStart.ANYWHERE)
            @Autocompleter
            List<AutocompleteSuggestion<String>> getTimezones(String input) {
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
                
                try {

                    var current_state = bot_data.getServerData(server)
                            .flatMap((e) -> e.getUserData(user))
                            .flatMap(UserData::isTimezoneVisible)
                            .orElse(true);
                    
                    if (current_state == state)
                        return String.format("Your timezone visibility is already set to %s", current_state);
                    
                    var data = bot_data.obtainServerData(server).obtainUserData(interaction.getUser());
                    
                    data.setTimezoneVisible(state);
                    
                    saveAsync();
                    
                    return String.format("""
                            Set timezone visibility to be %s in this server
                            Preview: %s
                            """, state, previewTime(interaction));
                } catch (DateTimeException e) {
                    return String.format("Timezone of id '%s' not found", state);
                }
            }
            
            @Command(name = "remove", description = "remove timezone information from the bot")
            @ReturnsResponse(ephemeral = true)
            String remove(@Interaction SlashCommandInteraction interaction) {
                var server = interaction.getServer().orElseThrow();
                var user = interaction.getUser();
                
                var data = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(user));
                
                if (data.flatMap(UserData::getTimezone).isEmpty())
                    return "You don't have timezone information in this server already";
                
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
                    return String.format("Invalid DateTimeFormat pattern: `%s`", pattern);
                } catch (DateTimeException e) {
                    return "Pattern may only request timezone information in square brackets (to indicate optionalness)";
                }
            } else {
                var opt_data = bot_data.getServerData(server)
                        .flatMap((e) -> e.getUserData(interaction.getUser()));
                
                if (opt_data.flatMap(UserData::getFormatter).isEmpty())
                    return "No operation performed";
                
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
            if (opt_language_tag.isPresent()) {
                
                var language_tag = opt_language_tag.get();
                var locale = Locale.forLanguageTag(language_tag);
                
                if (LocaleUtils.isLanguageUndetermined(locale))
                    return String.format("Unrecognised language tag `%s`", language_tag);
                
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
                    return "No operation performed";
                
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
        
        @SearchSuggestions(at = MatchStart.ANYWHERE)
        @Autocompleter
        List<AutocompleteSuggestion<String>> getLocales(String input) {
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
                            String.format("%s (%s)", e.toString(), e.toLanguageTag()),
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
    }
}
