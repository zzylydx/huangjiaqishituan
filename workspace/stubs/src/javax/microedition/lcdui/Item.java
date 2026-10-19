package javax.microedition.lcdui;

/** MIDP Item 桩（计费 Form 使用，从不渲染）。 */
public abstract class Item {
    Command defaultCommand;
    ItemCommandListener itemCommandListener;

    public void setDefaultCommand(Command cmd) {
        this.defaultCommand = cmd;
    }

    public void setItemCommandListener(ItemCommandListener l) {
        this.itemCommandListener = l;
    }
}
