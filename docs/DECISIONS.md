# Decisões

- Cada execução substitui arquivos correspondentes, preservando arquivos extras no destino.
- A pré-verificação exige espaço livre para todos os arquivos de origem, mesmo quando há arquivos que serão substituídos. É uma estimativa conservadora; o espaço pode mudar durante a execução.
- Cada arquivo é gravado em temporário na pasta de destino e movido para o nome final. Uma falha pode deixar uma cópia parcial da árvore, mas não um arquivo final parcialmente escrito.
- Links simbólicos e arquivos especiais são recusados para evitar saída da árvore de destino e cópias ambíguas.
- A agenda diária é mantida em memória e executa apenas com o aplicativo aberto.
- A interrupção é cooperativa: verifica o pedido durante a transferência de cada arquivo, remove o temporário e preserva arquivos finais já concluídos. O percentual reflete arquivos concluídos, não bytes transferidos.
