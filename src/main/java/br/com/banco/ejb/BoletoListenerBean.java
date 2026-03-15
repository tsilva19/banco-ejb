package br.com.banco.ejb;

import br.com.banco.entity.Boleto;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/*
 * @MessageDriven → EJB especial que fica escutando uma fila JMS.
 * O container chama onMessage() automaticamente quando
 * uma mensagem chega na fila BoletoQueue.
 *
 * Diferença entre @Asynchronous e @MessageDriven:
 *   @Asynchronous  → chamado por outro EJB no mesmo servidor
 *   @MessageDriven → consome mensagens de fila externa (JMS/Artemis)
 *
 * Cenário real:
 *   Internet Banking publica ID do boleto na fila →
 *   MDB consome → delega processamento ao BoletoBean
 *
 * @ActivationConfigProperty → configura o listener:
 *   destinationLookup → nome JNDI da fila (criamos via CLI)
 *   destinationType   → Queue (um consumidor) ou Topic (broadcast)
 *   acknowledgeMode   → Auto = confirma recebimento automaticamente
 */
@MessageDriven(
    name = "BoletoListenerBean",
    activationConfig = {
        @ActivationConfigProperty(
            propertyName = "destinationLookup",
            propertyValue = "java:/jms/queue/BoletoQueue"
        ),
        @ActivationConfigProperty(
            propertyName = "destinationType",
            propertyValue = "jakarta.jms.Queue"
        ),
        @ActivationConfigProperty(
            propertyName = "acknowledgeMode",
            propertyValue = "Auto-acknowledge"
        )
    }
)
public class BoletoListenerBean implements MessageListener {

    @PersistenceContext(unitName = "bancoPU")
    private EntityManager em;

    @Inject
    private BoletoBean boletoBean;

    /*
     * onMessage() → método principal do MDB.
     * Chamado pelo container cada vez que chega
     * uma mensagem na BoletoQueue.
     *
     * Formato esperado: ID do boleto como texto.
     * Exemplo de mensagem: "42"
     *
     * REQUIRES_NEW → cada mensagem tem sua própria transação.
     * Se falhar, só aquela mensagem é revertida.
     * O container tenta reentrar a mensagem na fila (redelivery).
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void onMessage(Message message) {

        System.out.printf(
            "[BoletoMDB] [Thread: %s] Mensagem recebida%n",
            Thread.currentThread().getName());

        try {
            // Verifica se é o tipo esperado
            if (!(message instanceof TextMessage textMessage)) {
                System.err.println(
                    "[BoletoMDB] Tipo inválido: "
                    + message.getClass().getSimpleName());
                return;
            }

            String corpo = textMessage.getText().trim();
            System.out.println("[BoletoMDB] Conteúdo: " + corpo);

            Long boletoId = Long.parseLong(corpo);

            // Verifica se o boleto existe
            Boleto boleto = em.find(Boleto.class, boletoId);
            if (boleto == null) {
                System.err.println(
                    "[BoletoMDB] Boleto não encontrado: " + boletoId);
                return;
            }

            System.out.printf(
                "[BoletoMDB] Processando boleto ID=%d valor=R$ %.2f%n",
                boleto.getId(), boleto.getValor());

            // Delega ao BoletoBean (@Asynchronous)
            boletoBean.processarBoleto(boletoId);

            System.out.printf(
                "[BoletoMDB] Boleto %d enviado para processamento.%n",
                boletoId);

        } catch (NumberFormatException e) {
            // ID inválido — não relança para evitar loop infinito
            System.err.println(
                "[BoletoMDB] ID inválido na mensagem: " + e.getMessage());

        } catch (JMSException e) {
            // Erro JMS — relança para o container fazer rollback
            // e recolocar a mensagem na fila
            throw new RuntimeException("Erro ao ler mensagem JMS", e);
        }
    }
}
