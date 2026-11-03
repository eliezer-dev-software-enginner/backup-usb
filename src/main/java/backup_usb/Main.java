package backup_usb;

import megalodonte.application.MegalodonteApp;
import megalodonte.base.theme.ThemeManager;
import megalodonte.theme.DefaultTheme;

public final class Main {
    static void main() {
        ThemeManager.setTheme(new DefaultTheme());
        MegalodonteApp.appName("Backup USB");
        MegalodonteApp.appIcon("/icon.png");
        MegalodonteApp.run(context -> {
            context.useView(new HomeScreen(), 820, 650);
        });
    }
}
