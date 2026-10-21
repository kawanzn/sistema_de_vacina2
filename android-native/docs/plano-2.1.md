# Plano adicional 2.1.0

CTN01–CTN18 permanecem como histórico da versão 2.0.0. Nenhuma execução manual abaixo foi marcada
como aprovada sem aparelho.

| Caso | Técnica e dados | Resultado esperado | Situação |
| --- | --- | --- | --- |
| SYN01 | Equivalência: profissional, cidadão, senha errada, token ausente | Somente profissional ativo recebe a base. | API local automatizada |
| SYN02 | Estado: conectado → offline → conectado | Baixar base, cortar rede, cadastrar e agendar. Reabrir offline. Reconectar e conferir site e segundo celular. | Manual pendente |
| SYN03 | Estado: envio aceito → resposta perdida → repetir UUID | Uma única dose e uma única baixa. | API local automatizada; rede real pendente |
| SYN04 | Equivalência: registros distintos / mesmo lote | Combinar edições independentes; conflito no mesmo lote sem sobrescrever. | API local automatizada; dois aparelhos pendentes |
| SYN05 | Estado: fila salva → processo encerrado → reabrir | Fila e registros persistem até o envio. | Runner compilado; execução pendente |
| SYN06 | Limites: reserva negativa/maior que saldo e vínculo ausente | Recusar toda a tentativa e preservar banco anterior. | API local automatizada |
| SYN07 | Estado: token válido → senha alterada | Novo login exigido sem apagar pendências. | API local automatizada |
| SYN08 | Estado: edição enquanto resposta trafega | Preservar mudança independente para o próximo envio; conflito no mesmo registro. | Merge Android compilado; execução pendente |
| UI21 | Equivalência: unidades com/sem lotes | Escolher unidade antes da vacina; não mostrar lote de outra unidade. | Runner compilado; UI pendente |
| UI22 | Equivalência: acentos, CPF formatado e busca vazia | Encontrar registros corretos no cadastro, relatório e estoque. | Regras JVM aprovadas; UI pendente |
| UI23 | Estado: formulário → recriação de Activity | Preservar página, identificação e consulta. | Manual pendente |
| UI24 | Equivalência: livre, reservado, esgotado e vencido | Filtrar estoque de acordo com saldo e validade. | Regras JVM aprovadas; UI pendente |

Pré-condições: servidor com `ready: true`, conta profissional de teste, dois aparelhos, unidade,
lote e paciente fictícios e backup. Registrar versão, data, aparelhos, passos, resultado e evidência.
