package host;

import host.js.JS;
import host.js.JS.DecodeFail;
import host.js.JS.DecodeOk;
import host.js.JS.JSAudioBuffer;
import host.js.JS.JSAudioBufferSource;
import host.js.JS.JSAudioContext;
import org.teavm.jso.JSObject;

/**
 * 音频宿主：bg.mid/main.mid 已在构建期用 Gervill 渲染为 WAV 并以 base64 内嵌进 audio.js。
 * iOS 自动播放策略：AudioContext 首次用户手势时 resume；若 start() 时上下文尚未
 * running，挂起该次播放，等 statechange→running 后自动补播（消除开头无声/卡顿）。
 */
public final class HostAudio {
    private static final String[] KEYS = {"main", "bg"};
    private static JSAudioContext ctx;
    private static final JSAudioBuffer[] buffers = new JSAudioBuffer[KEYS.length];

    /** Player 桩实现它接收真正的播放句柄（可能是手势 resume 之后才到手）。 */
    public interface Sink {
        void attach(Object handle);
    }

    private static Sink pendingSink;
    private static int pendingTrack = -1;
    private static boolean pendingLoop;

    private HostAudio() {
    }

    public static void init() {
        try {
            ctx = JS.newAudioContext();
        } catch (Throwable t) {
            JS.error("no AudioContext: " + t);
            return;
        }
        ctx.setOnstatechange(new JS.JSFunc() {
            public void run() {
                flushPending();
            }
        });
        for (int i = 0; i < KEYS.length; i++) {
            final int idx = i;
            String url = JS.audioDataUrl(KEYS[i]);
            if (url == null) {
                JS.log("audio missing: " + KEYS[i]);
                continue;
            }
            String b64 = url.substring(url.indexOf(',') + 1);
            try {
                ctx.decodeAudioData(JS.arrayBufferFromBase64(b64), new DecodeOk() {
                    public void accept(JSAudioBuffer buf) {
                        buffers[idx] = buf;
                        JS.log("audio decoded: " + KEYS[idx]);
                    }
                }, new DecodeFail() {
                    public void accept(JSObject err) {
                        JS.error("audio decode failed: " + KEYS[idx]);
                    }
                });
            } catch (Throwable t) {
                JS.error("decodeAudioData threw: " + t);
            }
        }
    }

    /** 任意用户手势都尝试 resume（iOS 后台回来上下文可能重新挂起）。 */
    public static void ensureResumed() {
        if (ctx != null && !"running".equals(ctx.getState())) {
            try {
                ctx.resume();
            } catch (Throwable t) {
                // 忽略：部分浏览器手势策略差异
            }
        }
    }

    private static void flushPending() {
        if (ctx == null || pendingSink == null) {
            return;
        }
        if ("running".equals(ctx.getState())) {
            Sink s = pendingSink;
            int t = pendingTrack;
            boolean l = pendingLoop;
            pendingSink = null;
            pendingTrack = -1;
            try {
                s.attach(startSource(t, l));
            } catch (Throwable tt) {
                JS.error("deferred play failed: " + tt);
            }
        }
    }

    public static void play(Sink sink, int trackIndex, boolean loop) {
        if (ctx == null || trackIndex < 0 || trackIndex >= buffers.length || buffers[trackIndex] == null) {
            JS.log("audio buffer not ready for track " + trackIndex);
            return;
        }
        if (!"running".equals(ctx.getState())) {
            // 上下文被自动播放策略挂起：先记录，resume 后由 statechange 补播
            pendingSink = sink;
            pendingTrack = trackIndex;
            pendingLoop = loop;
            ensureResumed();
            JS.log("audio deferred while ctx=" + ctx.getState());
            return;
        }
        try {
            sink.attach(startSource(trackIndex, loop));
        } catch (Throwable t) {
            JS.error("play failed: " + t);
        }
    }

    private static JSAudioBufferSource startSource(int idx, boolean loop) {
        JSAudioBufferSource src = ctx.createBufferSource();
        src.setBuffer(buffers[idx]);
        src.setLoop(loop);
        src.connect(ctx.getDestination());
        src.start(0);
        return src;
    }

    public static void stop(Sink sink, Object handle) {
        if (sink == pendingSink) {
            pendingSink = null;
            pendingTrack = -1;
        }
        if (handle instanceof JSAudioBufferSource) {
            try {
                ((JSAudioBufferSource) handle).stop(0);
            } catch (Throwable t) {
                // 已停止时 stop 会抛错，忽略
            }
        }
    }
}
