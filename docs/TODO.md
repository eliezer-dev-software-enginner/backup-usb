# Pendências

- Reduzir o custo das sincronizações frequentes: hoje cada execução percorre toda a origem e compara byte a byte os arquivos de mesmo tamanho. Avaliar um índice de tamanho e data de modificação para evitar leituras repetidas, com verificação completa periódica para detectar alterações que preservem esses metadados.
- Reduzir o uso de RAM em árvores muito grandes: a varredura e o planejamento da cópia mantêm listas de caminhos em memória, fazendo o consumo crescer com a quantidade de arquivos e pastas. Avaliar processamento em lotes ou fluxo, preservando a verificação de espaço antes de iniciar a cópia e o progresso da operação.
- Validar por interação gráfica com um dispositivo USB real, inclusive remoção durante a cópia.
- Adicionar agendamento independente do aplicativo por serviço do sistema, se necessário.
- Adaptar a descoberta de dispositivos para outros sistemas operacionais.
