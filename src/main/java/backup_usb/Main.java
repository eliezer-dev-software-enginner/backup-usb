package backup_usb;

import megalodonte.application.MegalodonteApp;
import megalodonte.base.theme.ThemeManager;
import megalodonte.theme.DefaultTheme;

public final class Main {
    public static void main(String[] args) {
        ThemeManager.setTheme(new DefaultTheme());
        MegalodonteApp.appName("Backup USB");
        MegalodonteApp.run(context -> context.useView(new HomeScreen()));
    }
}
