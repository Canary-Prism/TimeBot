package canaryprism.timebot;

import canaryprism.slavacord.CustomChoiceName;

import java.util.Arrays;

public enum ResponderFlags implements CustomChoiceName {
    EPHEMERAL("ephemeral", 1 << 6),
    SILENT("silent", 1 << 12);

    private final String choice_name;
    private final int id;


    ResponderFlags(String choice_name, int id) {
        this.choice_name = choice_name;
        this.id = id;
    }

    @Override
    public String getCustomName() {
        return choice_name;
    }

    public int getId() {
        return id;
    }

    public static ResponderFlags fromId(int id) {
        return Arrays.stream(ResponderFlags.values())
                .filter((e) -> e.id == id)
                .findAny()
                .orElseThrow();
    }
}
