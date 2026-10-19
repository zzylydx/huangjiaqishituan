package javax.microedition.media;

import host.HostAudio;
import host.Host;

import java.io.InputStream;

/**
 * MMAPI 桩：本游戏只用 createPlayer(InputStream, "audio/midi") + setLoopCount/start/stop。
 * 音频内容与构建期预渲染的 WAV 按字节匹配（main.mid / bg.mid），未匹配则静音。
 */
public class Manager {
    public static final String[] KNOWN_MIDI = {"main.mid", "bg.mid"};
    private static byte[][] knownBytes;

    private Manager() {
    }

    public static Player createPlayer(InputStream stream, String contentType) {
        byte[] data = readAll(stream);
        int track = matchTrack(data);
        if (track < 0) {
            Host.log("player: unknown audio content (" + data.length + " bytes, " + contentType + ")");
        }
        return new PlayerImpl(track);
    }

    private static int matchTrack(byte[] data) {
        if (knownBytes == null) {
            knownBytes = new byte[KNOWN_MIDI.length][];
            for (int i = 0; i < KNOWN_MIDI.length; i++) {
                InputStream is = Manager.class.getResourceAsStream("/" + KNOWN_MIDI[i]);
                if (is != null) {
                    knownBytes[i] = readAll(is);
                }
            }
        }
        if (data == null || data.length < 4) {
            return -1;
        }
        for (int i = 0; i < knownBytes.length; i++) {
            byte[] k = knownBytes[i];
            if (k == null || k.length != data.length) {
                continue;
            }
            boolean same = true;
            for (int j = 0; j < k.length; j++) {
                if (k[j] != data[j]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] readAll(InputStream is) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(4096);
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            is.close();
            return bos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    static class PlayerImpl implements Player, HostAudio.Sink {
        private final int track; // -1 = 未知内容（静音）
        private boolean loop;
        private boolean started;
        private Object handle;

        PlayerImpl(int track) {
            this.track = track;
        }

        @Override
        public void attach(Object h) {
            this.handle = h;
        }

        public void setLoopCount(int count) {
            // MMAPI: 1=单次, -1=无限
            this.loop = count != 1;
        }

        public void start() {
            if (!started) {
                started = true;
                if (track >= 0) {
                    // 上下文未 running 时挂起，AudioContext 恢复后自动补播
                    HostAudio.play(this, track, loop);
                }
            }
        }

        public void stop() {
            if (started) {
                started = false;
                HostAudio.stop(this, handle);
                handle = null;
            }
        }

        public void close() {
            stop();
        }

        public void deallocate() {
            stop();
        }
    }
}
