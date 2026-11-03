# Decisões

- Cada execução transfere arquivos novos ou cujo conteúdo difere, substituindo os correspondentes e preservando extras no destino. Arquivos de mesmo tamanho são lidos e comparados para evitar falsos negativos quando datas coincidem.
- A pré-verificação exige espaço livre para o crescimento previsto mais o maior temporário. É conservadora; o espaço pode mudar durante a execução.
- Cada arquivo é gravado em temporário na pasta de destino e movido para o nome final. Uma falha pode deixar uma cópia parcial da árvore, mas não um arquivo final parcialmente escrito.
- Links simbólicos e arquivos especiais são recusados para evitar saída da árvore de destino e cópias ambíguas.
- `WatchService` observa a origem recursivamente; eventos são agrupados por dois segundos e disparam sincronização somente dentro da janela. A agenda periódica reconcilia a árvore inteira para cobrir eventos perdidos. Ambos funcionam apenas com o aplicativo aberto.
- A interrupção é cooperativa: verifica o pedido durante comparação e transferência de cada arquivo, remove o temporário e preserva arquivos finais já concluídos. Uma operação de I/O bloqueada no sistema operacional, como o fechamento do arquivo no USB, só pode ser interrompida depois que o dispositivo responder.
- O percentual da cópia reflete bytes transferidos nesta execução; a UI também informa quantos arquivos terminaram. Depois de enviar os bytes, o fechamento pode aguardar o USB confirmar a gravação antes de substituir o arquivo final.
