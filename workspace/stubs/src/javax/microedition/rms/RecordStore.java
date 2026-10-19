package javax.microedition.rms;

import host.HostStorage;
import host.js.JS;
import javax.microedition.lcdui.Image;

import java.util.Vector;

/**
 * MIDP RecordStore 桩 -> localStorage。
 * 存储格式（键 rms:<name>）：每条记录一行 "id:base64"，行间以 '\n' 分隔。
 * 游戏（f 类 / "buydata"）的用法是 getRecord(1) 与 addRecord，兼容即可。
 */
public class RecordStore {
    private final String name;
    private final Vector ids = new Vector();     // Integer
    private final Vector records = new Vector(); // byte[]
    private int nextId = 1;
    private boolean open = true;

    private RecordStore(String name) {
        this.name = name;
        load();
    }

    public static RecordStore openRecordStore(String recordStoreName, boolean createIfNecessary)
            throws RecordStoreException {
        String raw = HostStorage.get("rms:" + recordStoreName);
        if (raw == null && !createIfNecessary) {
            throw new RecordStoreNotFoundException(recordStoreName);
        }
        return new RecordStore(recordStoreName);
    }

    public static void deleteRecordStore(String recordStoreName) throws RecordStoreException {
        HostStorage.remove("rms:" + recordStoreName);
    }

    public static String[] listRecordStores() {
        // 游戏未用；无法从 localStorage 反向前缀，返回空表
        return new String[0];
    }

    public int addRecord(byte[] data, int offset, int numBytes) throws RecordStoreException {
        checkOpen();
        byte[] copy = new byte[numBytes];
        System.arraycopy(data, offset, copy, 0, numBytes);
        int id = nextId++;
        ids.addElement(new Integer(id));
        records.addElement(copy);
        flush();
        return id;
    }

    public void setRecord(int recordId, byte[] newData, int offset, int numBytes) throws RecordStoreException {
        checkOpen();
        int idx = indexOf(recordId);
        byte[] copy = new byte[numBytes];
        System.arraycopy(newData, offset, copy, 0, numBytes);
        if (idx >= 0) {
            records.setElementAt(copy, idx);
        } else {
            ids.addElement(new Integer(recordId));
            records.addElement(copy);
            if (recordId >= nextId) {
                nextId = recordId + 1;
            }
        }
        flush();
    }

    public byte[] getRecord(int recordId) throws RecordStoreException {
        checkOpen();
        int idx = indexOf(recordId);
        if (idx < 0) {
            throw new InvalidRecordIDException(String.valueOf(recordId));
        }
        byte[] src = (byte[]) records.elementAt(idx);
        byte[] copy = new byte[src.length];
        System.arraycopy(src, 0, copy, 0, src.length);
        return copy;
    }

    public void deleteRecord(int recordId) throws RecordStoreException {
        checkOpen();
        int idx = indexOf(recordId);
        if (idx < 0) {
            throw new InvalidRecordIDException(String.valueOf(recordId));
        }
        ids.removeElementAt(idx);
        records.removeElementAt(idx);
        flush();
    }

    public int getNumRecords() {
        return ids.size();
    }

    public int getSize() {
        int total = 0;
        for (int i = 0; i < records.size(); i++) {
            total += ((byte[]) records.elementAt(i)).length;
        }
        return total;
    }

    public int getSizeAvailable() {
        return 1 << 20;
    }

    public String getName() {
        return name;
    }

    public void closeRecordStore() throws RecordStoreException {
        checkOpen();
        open = false;
        flush();
    }

    // ---------- 持久化 ----------

    private void checkOpen() throws RecordStoreNotOpenException {
        if (!open) {
            throw new RecordStoreNotOpenException(name);
        }
    }

    private int indexOf(int recordId) {
        for (int i = 0; i < ids.size(); i++) {
            if (((Integer) ids.elementAt(i)).intValue() == recordId) {
                return i;
            }
        }
        return -1;
    }

    private void load() {
        String raw = HostStorage.get("rms:" + name);
        if (raw == null) {
            return;
        }
        try {
            int pos = 0;
            int len = raw.length();
            while (pos < len) {
                int colon = raw.indexOf(':', pos);
                int nl = raw.indexOf('\n', pos);
                if (colon < 0) {
                    break;
                }
                int end = nl < 0 ? len : nl;
                int id = Integer.parseInt(raw.substring(pos, colon));
                String b64 = raw.substring(colon + 1, end);
                byte[] data = b64ToBytes(b64);
                ids.addElement(new Integer(id));
                records.addElement(data);
                if (id >= nextId) {
                    nextId = id + 1;
                }
                pos = end + 1;
            }
        } catch (Throwable t) {
            JS.error("rms load corrupted: " + name + " (" + t + ")");
        }
    }

    private void flush() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            int id = ((Integer) ids.elementAt(i)).intValue();
            byte[] data = (byte[]) records.elementAt(i);
            sb.append(id).append(':').append(Image.base64Encode(data, 0, data.length));
            if (i < ids.size() - 1) {
                sb.append('\n');
            }
        }
        HostStorage.set("rms:" + name, sb.toString());
    }

    private static byte[] b64ToBytes(String s) {
        int outLen = s.length();
        int pad = 0;
        while (pad < outLen && s.charAt(outLen - 1 - pad) == '=') {
            pad++;
        }
        byte[] out = new byte[outLen * 3 / 4 - pad];
        int o = 0;
        int acc = 0;
        int bits = 0;
        for (int i = 0; i < outLen; i++) {
            char c = s.charAt(i);
            int v;
            if (c >= 'A' && c <= 'Z') {
                v = c - 'A';
            } else if (c >= 'a' && c <= 'z') {
                v = c - 'a' + 26;
            } else if (c >= '0' && c <= '9') {
                v = c - '0' + 52;
            } else if (c == '+') {
                v = 62;
            } else if (c == '/') {
                v = 63;
            } else {
                continue; // '='
            }
            acc = (acc << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[o++] = (byte) ((acc >> bits) & 0xFF);
            }
        }
        return out;
    }
}
