package backup_usb;

import java.time.LocalTime;

final class BackupSchedule {
    private BackupSchedule() {}

    static boolean inWindow(LocalTime start, LocalTime end, LocalTime now) {
        return !now.isBefore(start) && !now.isAfter(end.withSecond(59).withNano(999_999_999));
    }

    static boolean due(LocalTime start, LocalTime end, int intervalMinutes, LocalTime now) {
        if (!inWindow(start, end, now)) return false;
        int minute = now.getHour() * 60 + now.getMinute();
        int first = start.getHour() * 60 + start.getMinute();
        return (minute - first) % intervalMinutes == 0;
    }
}
