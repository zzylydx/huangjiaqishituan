package host;

import host.js.JS;

/** localStorage 简单封装（键值均为字符串）。 */
public final class HostStorage {
    private HostStorage() {
    }

    public static String get(String key) {
        return JS.storageGet(key);
    }

    public static void set(String key, String value) {
        JS.storageSet(key, value);
    }

    public static void remove(String key) {
        JS.storageRemove(key);
    }
}
