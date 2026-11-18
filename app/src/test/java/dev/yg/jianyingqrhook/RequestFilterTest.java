package dev.yg.jianyingqrhook;

import org.junit.Test;
import static org.junit.Assert.*;

public class RequestFilterTest {
    @Test public void matchesScanAndConfirmationWithRedirectSlash() {
        assertTrue(RequestFilter.matches("https://lv.ulikecam.com/passport/mobile/scan_qrcode?aid=1775&token=test"));
        assertTrue(RequestFilter.matches("https://lv.ulikecam.com/passport/mobile/scan_qrcode/"));
        assertTrue(RequestFilter.matches("https://lv.ulikecam.com/passport/mobile/confirm_qrcode?decision=0"));
        assertTrue(RequestFilter.matches("https://lv.ulikecam.com/passport/mobile/confirm_qrcode/"));
    }
    @Test public void excludesUnrelatedTrafficAndLookalikeHosts() {
        assertFalse(RequestFilter.matches(null));
        assertFalse(RequestFilter.matches("invalid"));
        assertFalse(RequestFilter.matches("https://lv.ulikecam.com.evil.test/passport/mobile/scan_qrcode/"));
        assertFalse(RequestFilter.matches("https://lv.ulikecam.com/other?next=/passport/mobile/scan_qrcode"));
        assertFalse(RequestFilter.matches("https://lv.ulikecam.com/passport/mobile/scan_qrcode/extra"));
        assertFalse(RequestFilter.matches("https://lv.ulikecam.com/passport/web/check_qrconnect/"));
    }
}
