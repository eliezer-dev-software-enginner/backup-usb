# Contexto

Aplicativo desktop Linux criado pelo Megalodonte CLI em modo nativo. A tela usa componentes Megalodonte; `BackupViewModel` gerencia estado, persistência e agenda; `BackupService` faz a cópia; `UsbDevices` consulta montagens USB.

O destino é uma subpasta em um USB montado. A seleção de dispositivo é visual, e o caminho de destino determina o dispositivo efetivo. A cópia roda em thread virtual e atualiza o status pela thread de UI.

O resultado de salvar definições e o progresso da cópia possuem estados separados. A UI reserva uma barra e uma mensagem para a cópia; o logger registra a contagem e o percentual a cada arquivo.

Salvar inicia o monitor recursivo da pasta de origem. Eventos são agrupados por dois segundos e respeitam a janela diária; o agendador periódico reconcilia arquivos mesmo se algum evento for perdido. A cópia compara conteúdo e transfere só arquivos diferentes.

A janela inicial mede 820 × 650, usando `Context.useView(component, width, height)`. O texto de instrução do agendamento usa `TextFlow` para quebrar linhas em larguras menores.

O ícone autoral fica em `src/main/resources/icon.svg`, com PNG correspondente em `icon.png`; o app usa `MegalodonteApp.appIcon` e o empacotamento usa o PNG.
No Linux, `Main.AppHost` fornece um `WM_CLASS` exclusivo. O registro de desenvolvimento do framework usa um nome de ícone derivado do conteúdo, evitando a imagem cacheada após atualização.
