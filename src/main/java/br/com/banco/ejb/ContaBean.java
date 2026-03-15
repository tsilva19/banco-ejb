package br.com.banco.ejb;

import br.com.banco.entity.Conta;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/*
 * @Stateless → EJB sem estado entre chamadas.
 *
 * O container mantém um POOL de instâncias.
 * Quando uma requisição chega, o container pega uma instância
 * do pool, executa o método e devolve ao pool.
 *
 * Cada método tem sua própria transação JTA — commit automático
 * ao final do método, rollback automático se lançar exceção.
 *
 * Equivalente ao @Service do Spring Boot.
 */
@Stateless
public class ContaBean {

    /*
     * @PersistenceContext → injeta o EntityManager gerenciado
     *                       pelo container WildFly.
     *
     * unitName → deve bater com o nome no persistence.xml ("bancoPU").
     *
     * NUNCA crie EntityManager manualmente em EJB!
     * O container gerencia o ciclo de vida e as transações.
     *
     * Equivalente ao @Autowired EntityManager do Spring.
     */
    @PersistenceContext(unitName = "bancoPU")
    private EntityManager em;

    // ================================================
    // CREATE
    // ================================================

    /*
     * @TransactionAttribute(REQUIRED) → padrão do @Stateless.
     * Se já houver transação ativa, usa ela.
     * Senão, o container cria uma nova automaticamente.
     * Commit acontece ao final do método.
     * Rollback automático se lançar RuntimeException.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Conta criar(Conta conta) {
        em.persist(conta);
        System.out.println("[ContaBean] Conta criada: " + conta);
        return conta;
    }

    // ================================================
    // READ
    // ================================================

    /*
     * @TransactionAttribute(SUPPORTS) → usa transação se houver,
     *                                   senão executa sem.
     * Ideal para leituras — não cria transação desnecessária.
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Optional<Conta> buscarPorId(Long id) {
        Conta conta = em.find(Conta.class, id);
        return Optional.ofNullable(conta);
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Conta> listarTodas() {
        return em.createQuery(
            "SELECT c FROM Conta c ORDER BY c.criadaEm DESC",
            Conta.class)
                 .getResultList();
    }

    // ================================================
    // UPDATE — operações financeiras
    // ================================================

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Conta atualizar(Conta conta) {
        /*
         * merge() → sincroniza entidade "detached" com o banco.
         * Use quando a entidade veio de fora da transação atual
         * (ex: chegou via REST, foi serializada e desserializada).
         *
         * Diferença persist vs merge:
         * persist → entidade nova (INSERT)
         * merge   → entidade existente (UPDATE)
         */
        return em.merge(conta);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Conta creditar(Long contaId, BigDecimal valor) {
        Conta conta = em.find(Conta.class, contaId);

        if (conta == null) {
            throw new IllegalArgumentException(
                "Conta não encontrada: " + contaId);
        }
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Valor deve ser positivo");
        }

        /*
         * Não precisa chamar merge() aqui!
         * A entidade foi carregada pelo em.find() dentro desta
         * transação — está no estado "managed".
         * Qualquer alteração é detectada automaticamente pelo JPA
         * (dirty checking) e salva no commit.
         */
        conta.setSaldo(conta.getSaldo().add(valor));

        System.out.printf("[ContaBean] Crédito R$ %.2f na conta %s. Saldo: R$ %.2f%n",
                          valor, conta.getNumeroConta(), conta.getSaldo());
        return conta;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Conta debitar(Long contaId, BigDecimal valor) {
        Conta conta = em.find(Conta.class, contaId);

        if (conta == null) {
            throw new IllegalArgumentException(
                "Conta não encontrada: " + contaId);
        }
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Valor deve ser positivo");
        }
        if (conta.getSaldo().compareTo(valor) < 0) {
            throw new IllegalStateException("Saldo insuficiente");
        }

        conta.setSaldo(conta.getSaldo().subtract(valor));

        System.out.printf("[ContaBean] Débito R$ %.2f na conta %s. Saldo: R$ %.2f%n",
                          valor, conta.getNumeroConta(), conta.getSaldo());
        return conta;
    }

    // ================================================
    // DELETE — soft delete (só desativa)
    // ================================================

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void desativar(Long id) {
        Conta conta = em.find(Conta.class, id);
        if (conta == null) {
            throw new IllegalArgumentException(
                "Conta não encontrada: " + id);
        }
        conta.setAtiva(false);
        System.out.println("[ContaBean] Conta desativada: " + id);
    }
}
