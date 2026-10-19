package javax.microedition.lcdui;

import java.util.Vector;

/** MIDP Form 桩：计费类(e)构建但从不 setCurrent，仅维护条目表。 */
public class Form extends Displayable {
    private final String title;
    private final Vector items = new Vector();

    public Form(String title) {
        this.title = title;
    }

    public int append(String s) {
        items.addElement(new StringItem(null, s));
        return items.size() - 1;
    }

    public int append(Item item) {
        items.addElement(item);
        return items.size() - 1;
    }

    public Item get(int index) {
        return (Item) items.elementAt(index);
    }

    public int size() {
        return items.size();
    }

    public String getTitle() {
        return title;
    }
}
