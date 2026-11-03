package backup_usb;

import megalodonte.base.components.Component;
import megalodonte.base.components.ScreenComponent;
import megalodonte.components.Text;
import megalodonte.components.Button;
import megalodonte.components.ProgressBar;
import megalodonte.components.inputs.Input;
import megalodonte.components.layout_components.Column;
import megalodonte.components.layout_components.Row;
import megalodonte.props.ColumnProps;
import megalodonte.props.RowProps;
import megalodonte.props.TextProps;

public final class HomeScreen implements ScreenComponent {
    private final BackupViewModel model = new BackupViewModel();

    @Override public Component render() {
        return new Column(new ColumnProps().paddingAll(28).spacingOf(12)).children(
                new Text("Backup USB", new TextProps().fontSize(28)),
                new Text("Dispositivos USB montados (informação visual)"),
                new Text(model.devices),
                new Button("Atualizar dispositivos").onClick(model::refreshDevices),
                new Text("Pasta de origem"),
                new Row(new RowProps().spacingOf(8)).children(
                        new Input(model.source).fillWidth(),
                        new Button("Escolher origem").onClick(model::chooseSource)),
                new Text("Pasta de destino dentro do USB"),
                new Row(new RowProps().spacingOf(8)).children(
                        new Input(model.destination).fillWidth(),
                        new Button("Escolher destino").onClick(model::chooseDestination)),
                new Text("Agendamento diário: início e fim em HH:mm; intervalo em minutos. Use 00:00–23:59 para o dia inteiro."),
                new Row(new RowProps().spacingOf(8)).children(
                        new Input(model.start), new Input(model.end), new Input(model.interval)),
                new Row(new RowProps().spacingOf(8)).children(
                        new Button("Salvar definições").onClick(model::save),
                        new Button("Iniciar cópia agora").onClick(model::copyNow)),
                new Text("Definições"),
                new Text(model.status),
                new Text("Progresso da cópia"),
                new ProgressBar(model.copyPercent),
                new Text(model.copyStatus));
    }
}
