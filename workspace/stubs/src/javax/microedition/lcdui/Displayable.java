package javax.microedition.lcdui;

import java.util.Vector;
import host.Host;

/**
 * MIDP Displayable 桩。本游戏只把 Canvas 设为 current；
 * Form/StringItem 仅被计费类(e)构建、从不 setCurrent，纯数据壳即可。
 */
public abstract class Displayable {
    final Vector commands = new Vector();
    CommandListener commandListener;

    public void addCommand(Command cmd) {
        if (cmd != null && !commands.contains(cmd)) {
            commands.addElement(cmd);
        }
    }

    public void removeCommand(Command cmd) {
        commands.removeElement(cmd);
    }

    public void setCommandListener(CommandListener l) {
        this.commandListener = l;
    }

    /**
     * 逻辑分辨率与 FullCn 的硬编码一致（240x320）。
     */
    public int getWidth() {
        return Host.LOGICAL_W;
    }

    public int getHeight() {
        return Host.LOGICAL_H;
    }

    public boolean isShown() {
        return Host.attached;
    }
}
