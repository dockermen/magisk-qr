package dev.yg.jianyingqrhook;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

/** Desktop-visible log viewer for data emitted by the injected target process. */
public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView output;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        TextView title = new TextView(this);
        title.setText("剪映扫码请求记录");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        Button refresh = new Button(this);
        refresh.setText("刷新 Hook 数据");
        refresh.setOnClickListener(v -> refreshLogs());
        root.addView(refresh, new LinearLayout.LayoutParams(-1, -2));
        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setTextSize(12);
        output.setGravity(Gravity.TOP);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(output, new ViewGroup.LayoutParams(-1, -1));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        refreshLogs();
    }

    private void refreshLogs() {
        output.setText("读取中…");
        new Thread(() -> {
            StringBuilder text = new StringBuilder();
            try {
                java.lang.Process p = new ProcessBuilder("su", "-c", "logcat -d -v threadtime -s JYQrHook:I ZygiskJYQr:I '*:S'").start();
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line; while ((line = r.readLine()) != null) text.append(line).append('\n');
                int exit = p.waitFor();
                if (exit != 0) text.append("\n读取 logcat 失败，请在 Magisk 中允许本应用 Root 权限。");
            } catch (Exception e) { text.append("读取失败: ").append(e); }
            String display = decodeEvents(text.toString());
            handler.post(() -> output.setText(display.isEmpty()
                    ? "暂无扫码请求记录。\n确认 Magisk Root 权限已允许，然后重新扫码并刷新。"
                    : display));
        }).start();
    }

    private static String decodeEvents(String raw) {
        Map<String, String[]> chunks = new LinkedHashMap<>();
        StringBuilder result = new StringBuilder();
        for (String line : raw.split("\\r?\\n")) {
            int marker = line.indexOf("JYQR1 ");
            if (marker < 0) continue;
            String[] fields = line.substring(marker + 6).trim().split("\\s+", 4);
            if (fields.length != 4) continue;
            String[] part = fields[2].split("/");
            if (part.length != 2) continue;
            try {
                int index = Integer.parseInt(part[0]) - 1;
                int total = Integer.parseInt(part[1]);
                String key = fields[0] + ":" + fields[1];
                String[] pieces = chunks.get(key);
                if (pieces == null) { pieces = new String[total]; chunks.put(key, pieces); }
                if (total < 1 || total > 4096 || index < 0 || index >= total || pieces.length != total) continue;
                pieces[index] = fields[3];
                boolean complete = true;
                for (String piece : pieces) if (piece == null) { complete = false; break; }
                if (complete) {
                    StringBuilder encoded = new StringBuilder();
                    for (String piece : pieces) encoded.append(piece);
                    JSONObject event = new JSONObject(new String(Base64.decode(encoded.toString(), Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8));
                    result.append(event.toString(2)).append("\n\n");
                    chunks.remove(key);
                }
            } catch (Exception ignored) { }
        }
        if (result.length() == 0) {
            for (String line : raw.split("\\r?\\n")) {
                if (!line.trim().isEmpty() && !line.startsWith("--------- beginning of"))
                    result.append(line).append('\n');
            }
            if (result.length() == 0) result.append("没有采集事件。当前发布包未包含 Zygisk 原生库，请求 Hook 尚未接通。\n此提示与扫码操作是否完成无关。");
        }
        return result.toString();
    }
}
