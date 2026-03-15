package br.com.banco.ejb;

import br.com.banco.entity.Boleto;
import br.com.banco.entity.Boleto.StatusBoleto;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/*
 * @Singleton → UMA única instância na aplicação inteira.
 *              Ideal para agendamentos — evita execução paralela
 *              do mesmo timer.
 *
 * Diferença entre @Stateless e @Singleton:
 *   @Stateless → pool de instâncias, sem estado compartilhado
 *   @Singleton → uma instância, estado compartilhado,
 *                acesso serializado por padrão (thread-safe)
 *
 * @Startup → instanciado automaticamente quando o WildFly sobe.
 *            Sem @Startup, o Singleton só seria criado na
 *            primeira chamada.
 *
 * Equivalente ao @Component + @Scheduled do Spring Boot.
 */
@Singleton
@Startup
public class AgendamentoBean {

    @PersistenceContext(unitName = "bancoPU")
    private EntityManager em;

    /*
     * @Inject → injeta outro EJB via CDI.
     * Funciona igual ao @Autowired do Spring.
     * Podemos injetar EJBs dentro de outros EJBs assim.
     */
    @Inject
    private BoletoBean boletoBean;

    // ================================================
    // @Schedule — executa todo dia à meia-noite
    // ================================================

    /*
     * @Schedule → define um timer periódico.
     *
     * Parâmetros (expressão cron simplificada):
     *   second    → segundo (0-59)
     *   minute    → minuto  (0-59)
     *   hour      → hora    (0-23)
     *   dayOfWeek → dia da semana (* = todos)
     *   persistent→ false = não persiste o timer no banco
     *               (mais leve para estudo)
     *
     * Este exemplo: executa às 00:00:00 todo dia.
     * Equivalente ao @Scheduled(cron = "0 0 0 * * *") do Spring.
     */
    @Schedule(second = "0", minute = "0", hour = "0",
              dayOfWeek = "*", persistent = false)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void verificarBoletosVencidos() {

        System.out.println(
            "[AgendamentoBean] Verificando vencidos - "
            + LocalDateTime.now());

        List<Boleto> vencidos = em.createQuery(
            "SELECT b FROM Boleto b " +
            "WHERE b.status = :status " +
            "AND b.vencimento < :hoje",
            Boleto.class)
            .setParameter("status", StatusBoleto.PENDENTE)
            .setParameter("hoje", LocalDate.now())
            .getResultList();

        if (vencidos.isEmpty()) {
            System.out.println(
                "[AgendamentoBean] Nenhum boleto vencido.");
            return;
        }

        for (Boleto boleto : vencidos) {
            boleto.setStatus(StatusBoleto.VENCIDO);
            boleto.setMensagemErro(
                "Vencido em " + boleto.getVencimento());
            em.merge(boleto);
        }

        System.out.printf(
            "[AgendamentoBean] %d boleto(s) marcado(s) como VENCIDO.%n",
            vencidos.size());
    }

    // ================================================
    // Reprocessa boletos com ERRO — todo hora
    // ================================================

    /*
     * hour = "*"  → toda hora
     * minute = "0" → no minuto zero
     *
     * Equivalente ao @Scheduled(cron = "0 0 * * * *") do Spring.
     */
    @Schedule(second = "0", minute = "0",
              hour = "*", persistent = false)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void reprocessarBoletosComErro() {

        List<Boleto> comErro = em.createQuery(
            "SELECT b FROM Boleto b WHERE b.status = :status",
            Boleto.class)
            .setParameter("status", StatusBoleto.ERRO)
            .setMaxResults(10)
            .getResultList();

        if (comErro.isEmpty()) return;

        System.out.printf(
            "[AgendamentoBean] Reprocessando %d boleto(s).%n",
            comErro.size());

        for (Boleto boleto : comErro) {
            boleto.setStatus(StatusBoleto.PENDENTE);
            boleto.setMensagemErro(null);
            em.merge(boleto);

            // Dispara processamento assíncrono
            boletoBean.processarBoleto(boleto.getId());
        }
    }

    // ================================================
    // Health check — a cada 5 minutos
    // ================================================ 
     // minute = "*/5" → a cada 5 minutos.
     // Equivalente ao @Scheduled(fixedRate = 300000) do Spring.
     
    @Schedule(second = "0", minute = "*/5",
              hour = "*", persistent = false)
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public void healthCheck() {

        Long totalContas = em.createQuery(
            "SELECT COUNT(c) FROM Conta c WHERE c.ativa = true",
            Long.class).getSingleResult();

        Long pendentes = em.createQuery(
            "SELECT COUNT(b) FROM Boleto b WHERE b.status = :s",
            Long.class)
            .setParameter("s", StatusBoleto.PENDENTE)
            .getSingleResult();

        System.out.printf(
            "[AgendamentoBean] HEALTH [%s] | Contas: %d | Pendentes: %d%n",
            LocalDateTime.now(), totalContas, pendentes);
    }
}