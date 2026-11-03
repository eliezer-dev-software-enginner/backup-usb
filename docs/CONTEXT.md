# Contexto

Aplicativo desktop Linux criado pelo Megalodonte CLI em modo nativo. A tela usa componentes Megalodonte; `BackupViewModel` gerencia estado, persistência e agenda; `BackupService` faz a cópia; `UsbDevices` consulta montagens USB.

O destino é uma subpasta em um USB montado. A seleção de dispositivo é visual, e o caminho de destino determina o dispositivo efetivo. A cópia roda em thread virtual e atualiza o status pela thread de UI.
