package javax.microedition.lcdui;

/** MIDP Command 桩：仅存标签/类型/优先级。 */
public class Command {
    public static final int SCREEN = 1;
    public static final int BACK = 2;
    public static final int CANCEL = 3;
    public static final int OK = 4;
    public static final int HELP = 5;
    public static final int STOP = 6;
    public static final int EXIT = 7;
    public static final int ITEM = 8;

    private final String label;
    private final int commandType;
    private final int priority;

    public Command(String label, int commandType, int priority) {
        this.label = label;
        this.commandType = commandType;
        this.priority = priority;
    }

    public Command(String shortLabel, String longLabel, int commandType, int priority) {
        this(shortLabel, commandType, priority);
    }

    public String getLabel() {
        return label;
    }

    public int getCommandType() {
        return commandType;
    }

    public int getPriority() {
        return priority;
    }
}
