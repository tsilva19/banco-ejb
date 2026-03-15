package br.com.banco.ejb;

import br.com.banco.entity.Boleto;
import br.com.banco.entity.Boleto.StatusBoleto;
import br.com.banco.entity.Conta;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Stateless
public class BoletoBean {

    @PersistenceContext(unitName = "bancoPU")
    private EntityManager em;

    // ================================================
    // Emite um boleto — síncrono, retorna imediato
    // ================================================

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Boleto emitir(Long contaId, BigDecimal valor,
                         LocalDate vencimento, String beneficiario) {

        Conta conta = em.find(Conta.class, contaId);
        if (conta == null) {
            throw new IllegalArgumentException(
                "Conta não encontrada: " + contaId);
        }

        String codigoBarras = gerarCodigoBarras(conta, valor);
        Boleto boleto = new Boleto(conta, codigoBarras,
                                   valor, vencimento, beneficiario);
        em.persist(boleto);

        System.out.println("[BoletoBean] Boleto emitido: " + boleto);
        return boleto;
    }

    // ================================================
    // @Asynchronous — processa em background
    // ================================================

    /*
     * @Asynchronous → o método retorna IMEDIATAMENTE ao chamador.
     * O WildFly executa o corpo em uma thread separada do pool EJB.
     *
     * Fluxo:
     * 1. REST recebe requisição → chama processarBoleto()
     * 2. WildFly retorna controle para o REST na hora
     * 3. REST responde HTTP 202 para o cliente
     * 4. Em paralelo, WildFly executa processarBoleto() em background
     *
     * @TransactionAttribute(REQUIRES_NEW) → cria uma transação NOVA
     * separada da transação do chamador. Necessário porque o
     * @Asynchronous roda em thread diferente.
     *
     * Equivalente ao @Async do Spring Boot.
     */
    @Asynchronous
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void processarBoleto(Long boletoId) {

        System.out.printf(
            "[BoletoBean] [Thread: %s] Iniciando boleto %d%n",
            Thread.currentThread().getName(), boletoId);

        Boleto boleto = em.find(Boleto.class, boletoId);
        if (boleto == null) {
            System.err.println("[BoletoBean] Boleto não encontrado: "
                               + boletoId);
            return;
        }

        try {
            // Marca como processando e salva imediatamente
            boleto.setStatus(StatusBoleto.PROCESSANDO);
            em.merge(boleto);
            em.flush(); // força gravação antes do sleep

            // Simula latência de processamento (validação CIP, Febraban)
            TimeUnit.SECONDS.sleep(2);

            // Verifica vencimento
            if (boleto.getVencimento().isBefore(LocalDate.now())) {
                boleto.setStatus(StatusBoleto.VENCIDO);
                boleto.setMensagemErro(
                    "Vencido em " + boleto.getVencimento());

            } else {
                // Tenta debitar da conta
                Conta conta = boleto.getConta();

                if (conta.getSaldo().compareTo(boleto.getValor()) >= 0) {
                    conta.setSaldo(
                        conta.getSaldo().subtract(boleto.getValor()));
                    em.merge(conta);
                    boleto.setStatus(StatusBoleto.PAGO);
                    boleto.setProcessadoEm(LocalDateTime.now());
                } else {
                    boleto.setStatus(StatusBoleto.ERRO);
                    boleto.setMensagemErro("Saldo insuficiente");
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            boleto.setStatus(StatusBoleto.ERRO);
            boleto.setMensagemErro("Processamento interrompido");

        } catch (Exception e) {
            boleto.setStatus(StatusBoleto.ERRO);
            boleto.setMensagemErro("Erro: " + e.getMessage());
            System.err.println("[BoletoBean] ERRO no boleto "
                               + boletoId + ": " + e.getMessage());
        }

        em.merge(boleto);

        System.out.printf(
            "[BoletoBean] [Thread: %s] Boleto %d finalizado: %s%n",
            Thread.currentThread().getName(), boletoId, boleto.getStatus());
    }

    // ================================================
    // READ
    // ================================================

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Optional<Boleto> buscarPorId(Long id) {
        return Optional.ofNullable(em.find(Boleto.class, id));
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Boleto> listarPorConta(Long contaId) {
        return em.createQuery(
            "SELECT b FROM Boleto b WHERE b.conta.id = :contaId " +
            "ORDER BY b.criadoEm DESC", Boleto.class)
                 .setParameter("contaId", contaId)
                 .getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Boleto> listarPendentes() {
        return em.createQuery(
            "SELECT b FROM Boleto b WHERE b.status = :status",
            Boleto.class)
                 .setParameter("status", StatusBoleto.PENDENTE)
                 .getResultList();
    }

    // ================================================
    // Auxiliares
    // ================================================

    private String gerarCodigoBarras(Conta conta, BigDecimal valor) {
        String base = "341"
            + conta.getAgencia()
            + conta.getNumeroConta().replaceAll("[^0-9]", "")
            + String.format("%010d",
                valor.multiply(new BigDecimal("100")).longValue());

        return base.length() >= 47
            ? base.substring(0, 47)
            : String.format("%-47s", base).replace(' ', '0');
    }
}
