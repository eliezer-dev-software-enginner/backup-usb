package backup_usb.data;

import megalodonte.base.state.State;

public record InputSource(int idx, State<String> source) {
}
