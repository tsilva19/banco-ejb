package br.com.banco.ejb;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;

/*
 * EJB responsável por PUBLICAR mensagens na fila JMS.
 *
 * O MDB (BoletoListenerBean) é o CONSUMIDOR.
 * Este bean é o PRODUTOR.
 *
 * @Resource → injeta recursos do servidor WildFly via JNDI.
 *             Diferente do @Inject que é CDI,
 *             o @Resource busca recursos registrados no servidor.
 */
@Stateless
public class JmsProducerBean {

    /*
     * ConnectionFactory → fábrica de conexões JMS.
     * O WildFly registra automaticamente em:
     * java:/JmsXA ou java:comp/DefaultJMSConnectionFactory
     */
    @Resource(lookup = "java:comp/DefaultJMSConnectionFactory")
    private ConnectionFactory connectionFactory;

    /*
     * Queue → referência à fila que criamos via CLI.
     * JNDI: java:/jms/queue/BoletoQueue
     */
    @Resource(lookup = "java:/jms/queue/BoletoQueue")
    private Queue boletoQueue;

    /*
     * Publica o ID do boleto na fila como TextMessage.
     *
     * JMSContext → API moderna do JMS 2.0, mais simples.
     * try-with-resources → fecha a conexão automaticamente.
     *
     * O MDB (BoletoListenerBean) vai consumir essa mensagem
     * automaticamente assim que chegar na fila.
     */
    public void publicarBoleto(Long boletoId) {
        try (JMSContext context =
                 connectionFactory.createContext()) {

            String mensagem = String.valueOf(boletoId);
            context.createProducer().send(boletoQueue, mensagem);

            System.out.printf(
                "[JmsProducerBean] Mensagem publicada na fila: %s%n",
                mensagem);
        }
    }
}