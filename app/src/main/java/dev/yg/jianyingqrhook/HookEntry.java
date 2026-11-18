package dev.yg.jianyingqrhook;

import android.os.Process;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Observes requests; does not change arguments, results, or buffer positions. */
public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "JYQrHook";
    private static final int MAX_BODY = 64 * 1024;
    private static final int CHUNK_SIZE = 2800;
    private static final AtomicLong IDS = new AtomicLong();
    private static final Map<Object, Capture> REQUESTS =
            Collections.synchronizedMap(new WeakHashMap<Object, Capture>());
    private static final Map<Object, Capture> PROVIDERS =
            Collections.synchronizedMap(new WeakHashMap<Object, Capture>());
    private static final Map<Object, PendingRead> READS =
            Collections.synchronizedMap(new WeakHashMap<Object, PendingRead>());
    private static final Set<Class<?>> INSTALLED =
            Collections.newSetFromMap(new IdentityHashMap<Class<?>, Boolean>());
    private static final Set<Class<?>> READ_HOOKS =
            Collections.newSetFromMap(new IdentityHashMap<Class<?>, Boolean>());
    private static final Set<Class<?>> SINK_HOOKS =
            Collections.newSetFromMap(new IdentityHashMap<Class<?>, Boolean>());
    private static final ThreadLocal<Boolean> INSTALLING = new ThreadLocal<>();

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!"com.lemon.lv".equals(p.packageName)) return;
        Log.i(TAG, "loaded process=" + p.processName + " pid=" + Process.myPid());
        XposedBridge.hookAllMethods(ClassLoader.class, "loadClass", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable() || !(param.getResult() instanceof Class)
                        || Boolean.TRUE.equals(INSTALLING.get())) return;
                Class<?> type = (Class<?>) param.getResult();
                if (supported(type.getName())) install(type);
            }
        });
        for (String name : new String[] {
                "org.chromium.net.impl.CronetUrlRequest",
                "org.chromium.net.impl.UrlRequestBuilderImpl",
                "org.chromium.net.impl.CronetUploadDataStream",
                "org.chromium.net.CronetUrlRequest",
                "com.bytedance.frameworks.baselib.network.http.cronet.impl.CronetUrlRequest",
                "com.bytedance.retrofit2.client.Request",
                "okhttp3.Request$Builder"
        }) {
            try { install(Class.forName(name, false, p.classLoader)); }
            catch (ClassNotFoundException ignored) { }
            catch (Throwable error) { diagnostic("resolve " + name, error); }
        }
    }

    private static boolean supported(String name) {
        return name.endsWith(".CronetUrlRequest")
                || name.endsWith(".UrlRequestBuilderImpl")
                || name.endsWith(".CronetUrlRequestBuilderImpl")
                || name.endsWith(".CronetUploadDataStream")
                || name.equals("com.bytedance.retrofit2.client.Request")
                || name.equals("okhttp3.Request$Builder");
    }

    private static void install(final Class<?> type) {
        synchronized (INSTALLED) {
            if (!INSTALLED.add(type)) return;
        }
        INSTALLING.set(true);
        try {
            XposedBridge.hookAllConstructors(type, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable()) return;
                    guarded(() -> {
                        Capture c = capture(p.thisObject);
                        for (Object arg : p.args) {
                            if (arg instanceof String && RequestFilter.matches((String) arg))
                                c.url = (String) arg;
                        }
                        snapshot(p.thisObject, c);
                        if (type.getName().endsWith(".CronetUploadDataStream")) {
                            for (Object arg : p.args) {
                                Capture owner = REQUESTS.get(arg);
                                if (owner != null && RequestFilter.matches(owner.url)) c = owner;
                            }
                            Object provider = field(p.thisObject, "mUploadDataProvider", "mDataProvider");
                            bindProvider(provider, c);
                        } else if (type.getName().equals("com.bytedance.retrofit2.client.Request")) {
                            emit(c, "retrofit-request");
                        }
                    });
                }
            });
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers())) continue;
                String name = method.getName();
                if (!(name.equals("addHeader") || name.equals("setHttpMethod")
                        || name.equals("setUploadDataProvider") || name.equals("start")
                        || name.equals("build") || name.equals("nativeAddRequestHeader"))) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        guarded(() -> {
                            Capture c = capture(p.thisObject);
                            snapshot(p.thisObject, c);
                            String n = p.method.getName();
                            if (n.equals("setHttpMethod") && p.args.length > 0)
                                c.method = String.valueOf(p.args[0]);
                            if (n.equals("setUploadDataProvider") && p.args.length > 0)
                                bindProvider(p.args[0], c);
                            if (n.equals("addHeader") || n.equals("nativeAddRequestHeader")) {
                                List<String> strings = new ArrayList<>();
                                for (Object arg : p.args) if (arg instanceof String) strings.add((String) arg);
                                if (strings.size() >= 2) {
                                    addHeader(c, strings.get(0), strings.get(1));
                                    if (c.started) emit(c, "late-header");
                                }
                            }
                            if (n.equals("start")) { c.started = true; emit(c, "cronet-start"); }
                        });
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.hasThrowable() || !p.method.getName().equals("build")
                                || p.getResult() == null) return;
                        guarded(() -> {
                            Capture c = capture(p.thisObject);
                            snapshot(p.thisObject, c);
                            snapshot(p.getResult(), c);
                            REQUESTS.put(p.getResult(), c);
                            emit(c, "request-built");
                        });
                    }
                });
            }
            Log.i(TAG, "hooked " + type.getName());
        } catch (Throwable error) { diagnostic("install " + type.getName(), error); }
        finally { INSTALLING.remove(); }
    }

    private static Capture capture(Object request) {
        synchronized (REQUESTS) {
            Capture c = REQUESTS.get(request);
            if (c == null) { c = new Capture(); REQUESTS.put(request, c); }
            return c;
        }
    }

    private static void snapshot(Object request, Capture c) {
        if (request == null) return;
        Object url = field(request, "mInitialUrl", "mUrl", "url");
        if (url != null && RequestFilter.matches(url.toString())) c.url = url.toString();
        Object method = field(request, "mInitialMethod", "mMethod", "method");
        if (method instanceof String) c.method = (String) method;
        if (!RequestFilter.matches(c.url)) return;
        Object headers = field(request, "mRequestHeaders", "mRequestHeadersList", "headers");
        if (headers instanceof Iterable) for (Object h : (Iterable<?>) headers) {
            if (h instanceof Map.Entry) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) h;
                addHeader(c, String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            } else {
                Object name = field(h, "name"), value = field(h, "value");
                if (name != null && value != null) addHeader(c, name.toString(), value.toString());
            }
        }
        Object pairs = field(headers, "namesAndValues");
        if (pairs instanceof String[]) {
            String[] array = (String[]) pairs;
            for (int i = 0; i + 1 < array.length; i += 2) addHeader(c, array[i], array[i + 1]);
        }
        // TypedByteArray in Retrofit stores its bytes here. Never call writeTo/read on a body.
        Object body = field(request, "body", "mBody");
        Object bytes = field(body, "bytes", "mBytes");
        if (bytes instanceof byte[]) {
            byte[] data = (byte[]) bytes;
            synchronized (c) {
                c.body = new String(data, 0, Math.min(data.length, MAX_BODY), StandardCharsets.UTF_8);
                c.bodyTruncated = data.length > MAX_BODY;
            }
        }
        Object stream = field(request, "mUploadDataStream");
        Object provider = field(stream, "mUploadDataProvider", "mDataProvider");
        if (provider == null) provider = field(request, "mUploadDataProvider");
        bindProvider(provider, c);
    }

    private static void bindProvider(Object provider, Capture c) {
        if (provider == null || !RequestFilter.matches(c.url)) return;
        PROVIDERS.put(provider, c);
        for (Class<?> type = provider.getClass(); type != null; type = type.getSuperclass()) {
            synchronized (READ_HOOKS) { if (!READ_HOOKS.add(type)) continue; }
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals("read") || method.getParameterTypes().length != 2
                        || !ByteBuffer.class.isAssignableFrom(method.getParameterTypes()[1])
                        || Modifier.isAbstract(method.getModifiers())) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        guarded(() -> {
                            Capture owner = PROVIDERS.get(p.thisObject);
                            if (owner == null || !(p.args[1] instanceof ByteBuffer)) return;
                            Object sink = p.args[0];
                            ByteBuffer buffer = (ByteBuffer) p.args[1];
                            hookSink(sink.getClass());
                            READS.put(sink, new PendingRead(owner, buffer, buffer.position()));
                        });
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.hasThrowable() && p.args.length > 0) READS.remove(p.args[0]);
                    }
                });
            }
        }
    }

    private static void hookSink(Class<?> sinkType) {
        for (Class<?> type = sinkType; type != null; type = type.getSuperclass()) {
            synchronized (SINK_HOOKS) { if (!SINK_HOOKS.add(type)) continue; }
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                if ((!name.equals("onReadSucceeded") && !name.equals("onReadError"))
                        || Modifier.isAbstract(method.getModifiers())) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        guarded(() -> {
                            PendingRead pending = READS.remove(p.thisObject);
                            if (pending == null || !p.method.getName().equals("onReadSucceeded")) return;
                            ByteBuffer copy = pending.buffer.duplicate();
                            int end = copy.position();
                            if (end < pending.start) return;
                            copy.limit(end);
                            copy.position(pending.start);
                            byte[] data = new byte[Math.min(copy.remaining(), MAX_BODY)];
                            boolean truncated = copy.remaining() > MAX_BODY;
                            copy.get(data);
                            synchronized (pending.capture) {
                                pending.capture.body = new String(data, StandardCharsets.UTF_8);
                                pending.capture.bodyTruncated = truncated;
                                pending.capture.uploadChunk++;
                            }
                            emit(pending.capture, "upload-chunk");
                        });
                    }
                });
            }
        }
    }

    private static Object field(Object object, String... names) {
        if (object == null) return null;
        for (String name : names) for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                Object value = f.get(object);
                if (value != null) return value;
                break;
            } catch (NoSuchFieldException ignored) { }
            catch (Throwable ignored) { break; }
        }
        return null;
    }

    private static void addHeader(Capture c, String name, String value) {
        synchronized (c) {
            for (String[] pair : c.headers)
                if (pair[0].equalsIgnoreCase(name) && pair[1].equals(value)) return;
            c.headers.add(new String[] {name, value});
        }
    }

    private static void emit(Capture c, String phase) throws Exception {
        synchronized (c) {
            if (!RequestFilter.matches(c.url)) return;
            JSONObject event = new JSONObject();
            event.put("id", c.id);
            event.put("pid", Process.myPid());
            event.put("time_ms", System.currentTimeMillis());
            event.put("phase", phase);
            event.put("method", c.method);
            event.put("url", c.url);
            JSONArray query = new JSONArray();
            String raw = new java.net.URI(c.url).getRawQuery();
            if (raw != null) for (String part : raw.split("&")) {
                int equals = part.indexOf('=');
                JSONObject item = new JSONObject();
                item.put("name", URLDecoder.decode(equals < 0 ? part : part.substring(0, equals), "UTF-8"));
                item.put("value", URLDecoder.decode(equals < 0 ? "" : part.substring(equals + 1), "UTF-8"));
                query.put(item);
            }
            event.put("query", query);
            JSONArray headers = new JSONArray();
            for (String[] h : c.headers) {
                JSONObject item = new JSONObject();
                item.put("name", h[0]); item.put("value", h[1]); headers.put(item);
            }
            event.put("headers", headers);
            event.put("body", c.body == null ? JSONObject.NULL : c.body);
            event.put("body_truncated", c.bodyTruncated);
            event.put("upload_chunk", c.uploadChunk);
            // Base64 chunks avoid splitting Unicode or losing long Cookie/signature values in logcat.
            String encoded = Base64.encodeToString(event.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            int count = (encoded.length() + CHUNK_SIZE - 1) / CHUNK_SIZE;
            long eventId = IDS.incrementAndGet();
            for (int i = 0; i < count; i++) Log.i(TAG, "JYQR1 " + Process.myPid() + " " + eventId
                    + " " + (i + 1) + "/" + count + " "
                    + encoded.substring(i * CHUNK_SIZE, Math.min(encoded.length(), (i + 1) * CHUNK_SIZE)));
        }
    }

    private static void guarded(Action action) {
        try { action.run(); }
        catch (Throwable error) { diagnostic("capture", error); }
    }

    private static void diagnostic(String operation, Throwable error) {
        Log.w(TAG, operation + ": " + error.getClass().getSimpleName());
    }

    private interface Action { void run() throws Exception; }
    private static final class Capture {
        final long id = IDS.incrementAndGet();
        String url;
        String method = "POST";
        final List<String[]> headers = new ArrayList<>();
        String body;
        boolean bodyTruncated;
        boolean started;
        int uploadChunk;
    }
    private static final class PendingRead {
        final Capture capture;
        final ByteBuffer buffer;
        final int start;
        PendingRead(Capture capture, ByteBuffer buffer, int start) {
            this.capture = capture; this.buffer = buffer; this.start = start;
        }
    }
}
