# Backup USB

Aplicativo desktop nativo Megalodonte para copiar uma pasta para um dispositivo USB montado. A lista de dispositivos é informativa; a pasta de destino define onde gravar.

A janela inicial tem 820 × 650 e o texto explicativo do agendamento se ajusta à largura disponível. O [ícone do aplicativo](src/main/resources/icon.svg) identifica a cópia de uma pasta para USB.
No Linux, a janela usa uma classe de inicialização própria para receber o ícone
correto no dock. Ao mudar o PNG, o Megalodonte cria um caminho de ícone novo
para evitar a imagem antiga em cache.

## Executar

Requer Linux com `lsblk`, JDK 25 e ambiente gráfico. Na raiz do projeto:

```sh
./gradlew test
./gradlew run
```

## Primeiro uso

1. Conecte e monte o USB; clique em **Atualizar dispositivos** para ver os pontos de montagem.
2. Escolha uma pasta de origem e uma subpasta de destino dentro do USB. A subpasta pode ser digitada mesmo que ainda não exista.
3. Defina início e fim da janela diária em `HH:mm` e o intervalo de reconciliação em minutos. Por exemplo, `09:00`, `17:00`, `60` monitora alterações nesse período e confere a árvore inteira a cada hora enquanto o aplicativo estiver aberto.
4. Clique em **Salvar definições** para persistir e ativar o agendamento. **Iniciar cópia agora** usa os campos atuais imediatamente; durante a execução, o mesmo botão vira **Interromper cópia**.

Alterações na origem disparam uma sincronização cerca de dois segundos após o último evento, dentro da janela configurada. A reconciliação periódica cobre eventos perdidos. `00:00–23:59` mantém o monitoramento ativo o dia inteiro. O aplicativo precisa permanecer aberto e o USB montado.

Cada execução lê arquivos de mesmo tamanho para comparar o conteúdo e **transfere apenas arquivos novos ou modificados**. Antes de escrever, exige espaço para o crescimento previsto e o maior arquivo temporário. Arquivos correspondentes são substituídos; extras no destino são preservados. Links simbólicos e arquivos especiais encontrados na origem são ignorados, sem interromper a cópia, e a quantidade aparece no resultado; links simbólicos no destino continuam recusados. O percentual aparece ao lado de **Progresso da cópia**, com barra e mensagem abaixo; **Definições** mostra o resultado de salvar. O avanço em bytes aparece durante a transferência e nos logs; depois, o USB pode demorar para confirmar a gravação antes de concluir o arquivo. Ao interromper, o arquivo em andamento é descartado e os já concluídos permanecem, assim que operações de I/O pendentes retornarem.

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
