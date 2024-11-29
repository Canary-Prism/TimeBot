package canaryprism.timebot;

import dev.dirs.ProjectDirectories;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.interaction.SlashCommand;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.NoSuchElementException;

@CommandLine.Command(
        subcommands = Main.SetToken.class
)
public class Main implements Runnable {
    
    private static final ProjectDirectories DIRS = ProjectDirectories.from("", "canaryprism", "TimeBot");
    
    
    @Override
    public void run() {
        
        try {
            var config_path = Path.of(DIRS.configDir);
            
            if (Files.notExists(config_path))
                Files.createDirectories(config_path);
            
            var token_file = config_path.resolve("token");
            
            var token = Files.readString(token_file);
            
            var api = new DiscordApiBuilder()
                    .setToken(token)
                    .setIntents(Intent.GUILD_MESSAGE_REACTIONS)
                    .login()
                    .join();
            
            var bot = new Bot(api, config_path.resolve("save.json"));
            bot.start();
        } catch (IOException e) {
            throw new NoSuchElementException("token file not found", e);
        }
    }
    
    @CommandLine.Command(name = "settoken")
    static class SetToken implements Runnable {
        
        @CommandLine.Parameters(index = "0")
        private String token;
        
        @Override
        public void run() {
            try {

                System.out.println("setting and writing token to file...");
                
                var config_path = Path.of(DIRS.configDir);
                
                if (Files.notExists(config_path))
                    Files.createDirectories(config_path);
                
                var token_file = config_path.resolve("token");
                
                Files.writeString(token_file, token,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                
                System.out.printf("token written to '%s'%n", token_file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    public static void main(String[] args) {
        new CommandLine(new Main()).execute(args);
    }
}