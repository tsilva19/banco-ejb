package br.com.banco.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.json.bind.annotation.JsonbTransient;

@Entity
@Table(name = "boleto")
public class Boleto {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "boleto_seq")
    @SequenceGenerator(name = "boleto_seq",
                       sequenceName = "boleto_id_seq",
                       allocationSize = 1)
    private Long id;

    /*
     * @ManyToOne → muitos boletos para uma conta.
     *              É o lado "filho" do relacionamento.
     *
     * fetch = LAZY → o JPA NÃO carrega a Conta automaticamente
     *                junto com o Boleto. Só busca quando você
     *                chamar boleto.getConta().
     *                Sempre prefira LAZY — evita consultas desnecessárias.
     *
     * @JoinColumn → coluna de chave estrangeira na tabela boleto.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id", nullable = false)
    @JsonbTransient
    private Conta conta;

    @Column(name = "codigo_barras", nullable = false, unique = true, length = 47)
    private String codigoBarras;

    @Column(name = "valor", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    /*
     * LocalDate → apenas data, sem hora.
     * Ideal para vencimento de boleto.
     * LocalDateTime → data + hora (usado em criadoEm, processadoEm).
     */
    @Column(name = "vencimento", nullable = false)
    private LocalDate vencimento;

    @Column(name = "beneficiario", nullable = false, length = 100)
    private String beneficiario;

    /*
     * Status do boleto — controlado pelo ciclo de vida.
     * PENDENTE   → recém criado
     * PROCESSANDO → sendo processado pelo @Asynchronous
     * PAGO        → processado com sucesso
     * ERRO        → falhou
     * VENCIDO     → detectado pelo @Schedule
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusBoleto status = StatusBoleto.PENDENTE;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "processado_em")
    private LocalDateTime processadoEm;

    @Column(name = "mensagem_erro", length = 500)
    private String mensagemErro;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
    }

    // Enum do ciclo de vida do boleto
    public enum StatusBoleto {
        PENDENTE, PROCESSANDO, PAGO, ERRO, VENCIDO
    }

    // Construtor vazio — obrigatório para o JPA
    public Boleto() {}

    public Boleto(Conta conta, String codigoBarras,
                  BigDecimal valor, LocalDate vencimento,
                  String beneficiario) {
        this.conta = conta;
        this.codigoBarras = codigoBarras;
        this.valor = valor;
        this.vencimento = vencimento;
        this.beneficiario = beneficiario;
    }

    // Getters e Setters
    public Long getId() { return id; }

    public Conta getConta() { return conta; }
    public void setConta(Conta conta) { this.conta = conta; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String c) { this.codigoBarras = c; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public LocalDate getVencimento() { return vencimento; }
    public void setVencimento(LocalDate vencimento) { this.vencimento = vencimento; }

    public String getBeneficiario() { return beneficiario; }
    public void setBeneficiario(String beneficiario) { this.beneficiario = beneficiario; }

    public StatusBoleto getStatus() { return status; }
    public void setStatus(StatusBoleto status) { this.status = status; }

    public LocalDateTime getCriadoEm() { return criadoEm; }

    public LocalDateTime getProcessadoEm() { return processadoEm; }
    public void setProcessadoEm(LocalDateTime p) { this.processadoEm = p; }

    public String getMensagemErro() { return mensagemErro; }
    public void setMensagemErro(String mensagemErro) { this.mensagemErro = mensagemErro; }

    @Override
    public String toString() {
        return "Boleto{id=" + id +
               ", codigoBarras='" + codigoBarras + "'" +
               ", valor=" + valor +
               ", status=" + status + "}";
    }
}