package dev.yg.jianyingqrhook;

import java.net.URI;

final class RequestFilter {
    static boolean matches(String url) {
        if (url == null) return false;
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"lv.ulikecam.com".equalsIgnoreCase(uri.getHost())) return false;
            String path = uri.getPath();
            return "/passport/mobile/scan_qrcode".equals(path)
                    || "/passport/mobile/scan_qrcode/".equals(path)
                    || "/passport/mobile/confirm_qrcode".equals(path)
                    || "/passport/mobile/confirm_qrcode/".equals(path);
        } catch (Exception ignored) {
            return false;
        }
    }
}
