# Backup USB

Aplicativo desktop nativo Megalodonte para copiar uma pasta para um dispositivo USB montado. A lista de dispositivos é informativa; a pasta de destino define onde gravar.

A janela inicial tem 820 × 650 e o texto explicativo do agendamento se ajusta à largura disponível. O [ícone do aplicativo](src/main/resources/icon.svg) identifica a cópia de uma pasta para USB.

## Executar

Requer Linux com `lsblk`, JDK 25 e ambiente gráfico. Na raiz do projeto:

```sh
./gradlew test
./gradlew run
```

## Primeiro uso

1. Conecte e monte o USB; clique em **Atualizar dispositivos** para ver os pontos de montagem.
2. Escolha uma pasta de origem e uma subpasta de destino dentro do USB. A subpasta pode ser digitada mesmo que ainda não exista.
3. Defina início e fim da janela diária em `HH:mm` e o intervalo em minutos. Por exemplo, `09:00`, `17:00`, `60` executa a cada hora entre esses horários enquanto o aplicativo estiver aberto.
4. Clique em **Salvar definições** para persistir e ativar o agendamento. **Iniciar cópia agora** usa os campos atuais imediatamente; durante a execução, o mesmo botão vira **Interromper cópia**.

Antes da cópia, o aplicativo exige espaço livre equivalente ao tamanho total dos arquivos de origem. Arquivos com o mesmo nome no destino são substituídos; arquivos extras são preservados. Links simbólicos e arquivos especiais não são copiados. O percentual aparece ao lado de **Progresso da cópia**, com barra e mensagem abaixo; **Definições** mostra o resultado de salvar. O terminal registra cada arquivo concluído. Ao interromper, o arquivo em andamento é descartado e os arquivos já concluídos permanecem no destino.

As definições ficam em `~/.config/backup-usb/settings.properties`. O agendamento funciona somente enquanto o aplicativo está aberto.

## Empacotar

`python3 scripts/package.py build appimage` cria um AppImage no Linux. Consulte [o workflow](.github/workflows/package.yml) para os requisitos dos instaladores.

## Código

A tela e serviços estão em `src/main/`. Veja [contexto](docs/CONTEXT.md), [decisões](docs/DECISIONS.md) e [pendências](docs/TODO.md).

## Skills para agentes

Este projeto inclui [`skills/megalodonte-native-ui/SKILL.md`](skills/megalodonte-native-ui/SKILL.md)
para interface e [`skills/criterioso/SKILL.md`](skills/criterioso/SKILL.md)
para investigação e entrega de mudanças.
O `AGENTS.md` exige que agentes leiam as skills pertinentes; isso
funciona mesmo sem instalá-la no catálogo. Para usar `$` no Codex, execute na
raiz do projeto:

```sh
megalodonte skills install --project .
```

O comando cria vínculos para as duas skills em `~/.codex/skills` (ou
`$CODEX_HOME/skills`), sem duplicar os arquivos. Reinicie a conversa se o menu
de `$` não atualizar. Após atualizar o Megalodonte CLI, sincronize as versões
mais recentes com:

```sh
megalodonte skills sync --project .
```

O projeto mantém cópias versionadas; a instalação no Codex acompanha as
alterações dessas cópias automaticamente.
