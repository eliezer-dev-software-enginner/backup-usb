package backup_usb;

import org.junit.jupiter.api.Test;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;

class BackupScheduleTest {
    @Test void fullDayWindowAcceptsChangesUntilEndOfDay() {
        LocalTime start = LocalTime.of(0, 0);
        LocalTime end = LocalTime.of(23, 59);
        assertTrue(BackupSchedule.inWindow(start, end, LocalTime.of(0, 0)));
        assertTrue(BackupSchedule.inWindow(start, end, LocalTime.of(23, 59, 59)));
        assertTrue(BackupSchedule.due(start, end, 60, LocalTime.of(12, 0)));
        assertFalse(BackupSchedule.due(start, end, 60, LocalTime.of(12, 1)));
    }

    @Test void narrowerWindowRejectsOutsideTimes() {
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(17, 0);
        assertFalse(BackupSchedule.inWindow(start, end, LocalTime.of(8, 59)));
        assertTrue(BackupSchedule.inWindow(start, end, LocalTime.of(17, 0, 30)));
        assertFalse(BackupSchedule.inWindow(start, end, LocalTime.of(17, 1)));
    }
}
