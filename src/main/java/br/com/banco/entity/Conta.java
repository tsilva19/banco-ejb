package br.com.banco.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
 * @Entity  → diz ao JPA que essa classe é uma entidade gerenciada.
 *            O JPA vai criar a tabela correspondente no banco.
 *
 * @Table   → define o nome da tabela no banco.
 *            Se omitir, usa o nome da classe.
 */
@Entity
@Table(name = "conta")
public class Conta {

    /*
     * @Id             → chave primária da tabela
     * @GeneratedValue → valor gerado automaticamente
     * SEQUENCE        → usa sequence do PostgreSQL (mais eficiente)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "conta_seq")
    @SequenceGenerator(name = "conta_seq",
                       sequenceName = "conta_id_seq",
                       allocationSize = 1)
    private Long id;

    /*
     * @Column → mapeia o campo para uma coluna da tabela.
     * nullable = false → NOT NULL no banco
     * length = 100     → VARCHAR(100)
     */
    @Column(name = "titular", nullable = false, length = 100)
    private String titular;

    @Column(name = "numero_conta", nullable = false, unique = true, length = 20)
    private String numeroConta;

    @Column(name = "agencia", nullable = false, length = 10)
    private String agencia;

    /*
     * BigDecimal → SEMPRE use para valores monetários.
     * Nunca use double ou float para dinheiro!
     * precision=15, scale=2 → até 999.999.999.999,99
     */
    @Column(name = "saldo", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo;

    /*
     * @Enumerated(STRING) → salva o nome do enum como texto no banco.
     * Ex: "CORRENTE", "POUPANCA"
     * Se usar ORDINAL salva o número (0,1,2) — evite, é frágil.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoConta tipo;

    @Column(name = "ativa", nullable = false)
    private boolean ativa = true;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private LocalDateTime criadaEm;

    @Column(name = "atualizada_em")
    private LocalDateTime atualizadaEm;

    /*
     * @PrePersist → executado automaticamente ANTES de salvar.
     * Equivalente ao @CreatedDate do Spring Data.
     */
    @PrePersist
    public void prePersist() {
        this.criadaEm = LocalDateTime.now();
        this.atualizadaEm = LocalDateTime.now();
        if (this.saldo == null) {
            this.saldo = BigDecimal.ZERO;
        }
    }

    /*
     * @PreUpdate → executado automaticamente ANTES de atualizar.
     * Equivalente ao @LastModifiedDate do Spring Data.
     */
    @PreUpdate
    public void preUpdate() {
        this.atualizadaEm = LocalDateTime.now();
    }

    // Enum dos tipos de conta
    public enum TipoConta {
        CORRENTE, POUPANCA, SALARIO
    }

    // Construtor vazio — obrigatório para o JPA
    public Conta() {}

    public Conta(String titular, String numeroConta,
                 String agencia, TipoConta tipo) {
        this.titular = titular;
        this.numeroConta = numeroConta;
        this.agencia = agencia;
        this.tipo = tipo;
    }

    // Getters e Setters
    public Long getId() { return id; }

    public String getTitular() { return titular; }
    public void setTitular(String titular) { this.titular = titular; }

    public String getNumeroConta() { return numeroConta; }
    public void setNumeroConta(String numeroConta) { this.numeroConta = numeroConta; }

    public String getAgencia() { return agencia; }
    public void setAgencia(String agencia) { this.agencia = agencia; }

    public BigDecimal getSaldo() { return saldo; }
    public void setSaldo(BigDecimal saldo) { this.saldo = saldo; }

    public TipoConta getTipo() { return tipo; }
    public void setTipo(TipoConta tipo) { this.tipo = tipo; }

    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }

    public LocalDateTime getCriadaEm() { return criadaEm; }
    public LocalDateTime getAtualizadaEm() { return atualizadaEm; }

    @Override
    public String toString() {
        return "Conta{id=" + id +
               ", titular='" + titular + "'" +
               ", numero='" + numeroConta + "'}";
    }
}