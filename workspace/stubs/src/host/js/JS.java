package host.js;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * 浏览器 JS 互操作覆盖层（自包含，仅依赖 teavm-jso 注解）。
 * 约定：index.html 提供 <canvas id="game" width=720 height=960>，并预载 audio.js
 * （window.__audio = {main|bg: dataURL}）。
 */
public final class JS {
    private JS() {
    }

    // ---------- DOM / Canvas ----------

    @JSBody(params = {}, script = "return document.getElementById('game');")
    public static native JSCanvas gameCanvas();

    @JSBody(params = {"tag"}, script = "return document.createElement(tag);")
    public static native JSElement createElement(String tag);

    // ---------- 输入桥（JS 虚拟按键 -> Java） ----------

    @JSBody(params = {"fn"}, script = "window.__padJava = fn;")
    public static native void registerPad(PadHandler fn);

    @JSFunctor
    public interface PadHandler extends JSObject {
        void call(int keyCode, boolean down);
    }

    // ---------- 缩放桥（JS 计算画布尺寸，Java 侧同步 Graphics 变换） ----------

    @JSBody(params = {}, script = "return window.__gscaleX || 3;")
    public static native double hostScaleX();

    @JSBody(params = {}, script = "return window.__gscaleY || 4;")
    public static native double hostScaleY();

    @JSBody(params = {"fn"}, script = "window.__resizeJava = fn;")
    public static native void registerResize(ResizeHandler fn);

    @JSFunctor
    public interface ResizeHandler extends JSObject {
        void call(double scaleX, double scaleY);
    }

    // ---------- 存储 ----------

    @JSBody(params = {"k"}, script = "try { return window.localStorage.getItem(k); } catch (e) { return null; }")
    public static native String storageGet(String k);

    @JSBody(params = {"k", "v"}, script = "try { window.localStorage.setItem(k, v); } catch (e) { }")
    public static native void storageSet(String k, String v);

    @JSBody(params = {"k"}, script = "try { window.localStorage.removeItem(k); } catch (e) { }")
    public static native void storageRemove(String k);

    // ---------- 音频 ----------

    @JSBody(params = {}, script = "return new (window.AudioContext || window.webkitAudioContext)();")
    public static native JSAudioContext newAudioContext();

    @JSBody(params = {"key"}, script = "return (window.__audio && window.__audio[key]) || null;")
    public static native String audioDataUrl(String key);

    @JSBody(params = {"b64"}, script = ""
            + "var bin = atob(b64), len = bin.length, arr = new Uint8Array(len);"
            + "for (var i = 0; i < len; i++) { arr[i] = bin.charCodeAt(i); }"
            + "return arr.buffer;")
    public static native JSObject arrayBufferFromBase64(String b64);

    // ---------- 调试 ----------

    @JSBody(params = {"s"}, script = "console.log('[host] ' + s);")
    public static native void log(String s);

    @JSBody(params = {"s"}, script = "console.error('[host] ' + s);")
    public static native void error(String s);

    // ---------- 覆盖接口 ----------

    public interface JSCanvas extends JSObject {
        @JSProperty
        int getWidth();

        @JSProperty
        int getHeight();

        JSCtx getContext(String type);

        void addEventListener(String type, JSFunc fn);
    }

    public interface JSElement extends JSObject {
        @JSProperty
        void setSrc(String src);

        @JSProperty
        int getWidth();

        @JSProperty
        int getHeight();

        void addEventListener(String type, JSFunc fn);
    }

    public interface JSFunc extends JSObject {
        void run();
    }

    public interface JSCtx extends JSObject {
        @JSProperty
        void setFillStyle(String v);

        @JSProperty
        void setStrokeStyle(String v);

        @JSProperty
        void setFont(String v);

        @JSProperty
        void setTextBaseline(String v);

        @JSProperty
        void setLineWidth(double v);

        @JSProperty
        void setImageSmoothingEnabled(boolean v);

        void setTransform(double a, double b, double c, double d, double e, double f);

        void save();

        void restore();

        void beginPath();

        void rect(double x, double y, double w, double h);

        void clip();

        void fill();

        void stroke();

        void moveTo(double x, double y);

        void lineTo(double x, double y);

        void fillRect(double x, double y, double w, double h);

        void clearRect(double x, double y, double w, double h);

        void arc(double x, double y, double r, double a0, double a1);

        void ellipse(double x, double y, double rx, double ry, double rot, double a0, double a1);

        void drawImage(JSObject img, double x, double y);

        void fillText(String s, double x, double y);

        JSTextMetrics measureText(String s);
    }

    public interface JSTextMetrics extends JSObject {
        @JSProperty
        double getWidth();
    }

    public interface JSAudioContext extends JSObject {
        @JSProperty
        JSAudioNode getDestination();

        @JSProperty
        String getState();

        @JSProperty
        void setOnstatechange(JSFunc fn);

        JSAudioBufferSource createBufferSource();

        void decodeAudioData(JSObject buffer, DecodeOk ok, DecodeFail fail);

        JSObject resume();
    }

    @JSFunctor
    public interface DecodeOk extends JSObject {
        void accept(JSAudioBuffer buf);
    }

    @JSFunctor
    public interface DecodeFail extends JSObject {
        void accept(JSObject err);
    }

    public interface JSAudioBuffer extends JSObject {
    }

    public interface JSAudioNode extends JSObject {
        void connect(JSAudioNode dst);
    }

    public interface JSAudioBufferSource extends JSAudioNode {
        @JSProperty
        void setBuffer(JSAudioBuffer buf);

        @JSProperty
        void setLoop(boolean loop);

        void start(double when);

        void stop(double when);
    }
}
