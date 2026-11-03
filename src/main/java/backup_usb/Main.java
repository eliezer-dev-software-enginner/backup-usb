package backup_usb;

import megalodonte.application.MegalodonteApp;
import megalodonte.application.MegalodonteApplication;
import megalodonte.base.theme.ThemeManager;
import megalodonte.theme.DefaultTheme;

public final class Main {
    public static final class AppHost extends MegalodonteApplication {}

    public static void main(String[] args) {
        ThemeManager.setTheme(new DefaultTheme());
        MegalodonteApp.appName("Backup USB");
        MegalodonteApp.appIcon("/icon.png");
        HomeScreen[] screen = new HomeScreen[1];
        MegalodonteApp.run(AppHost.class, args, context -> {
            screen[0] = new HomeScreen();
            context.useView(screen[0], 820, 650);
        }, event -> {
            if (event == MegalodonteApp.Event.CloseRequest && screen[0] != null) screen[0].onDestroy();
        });
    }
}
