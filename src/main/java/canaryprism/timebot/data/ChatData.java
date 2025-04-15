package canaryprism.timebot.data;

import canaryprism.timebot.ResponderFlags;
import net.dv8tion.jda.api.entities.User;

import java.util.Optional;

public interface ChatData {

    Optional<UserData> getUserData(User user);

    UserData obtainUserData(User user);

    Optional<ResponderFlags> getForcedMessageFlag();

}
