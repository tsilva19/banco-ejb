package br.com.banco.rest;

import br.com.banco.ejb.BoletoBean;
import br.com.banco.ejb.ContaBean;
import br.com.banco.ejb.JmsProducerBean;
import br.com.banco.entity.Boleto;
import br.com.banco.entity.Conta;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/*
 * @Path      → URL base deste recurso REST
 * @Produces  → formato da resposta (JSON)
 * @Consumes  → formato aceito no body (JSON)
 *
 * URLs disponíveis:
 *   GET    /api/contas                          → lista todas
 *   GET    /api/contas/{id}                     → busca por ID
 *   POST   /api/contas                          → cria conta
 *   DELETE /api/contas/{id}                     → desativa conta
 *   PUT    /api/contas/{id}/creditar?valor=X    → credita
 *   PUT    /api/contas/{id}/debitar?valor=X     → debita
 *   POST   /api/contas/{id}/boletos             → emite boleto
 *   POST   /api/contas/{id}/boletos/{bid}/processar → processa
 *   GET    /api/contas/{id}/boletos             → lista boletos
 *   GET    /api/contas/{id}/boletos/{bid}       → busca boleto
 */
@Path("/contas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContaResource {

    /*
     * @Inject → injeta os EJBs via CDI.
     * O container gerencia as instâncias.
     * Equivalente ao @Autowired do Spring.
     */
    @Inject
    private ContaBean contaBean;

    @Inject
    private BoletoBean boletoBean;
    
    @Inject
    private JmsProducerBean jmsProducerBean;

    // ================================================
    // Contas
    // ================================================

    @GET
    public Response listarContas() {
        List<Conta> contas = contaBean.listarTodas();
        return Response.ok(contas).build();
    }

    @GET
    @Path("/{id}")
    public Response buscarConta(@PathParam("id") Long id) {
        return contaBean.buscarPorId(id)
            .map(c -> Response.ok(c).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("erro", "Conta não encontrada: " + id))
                .build());
    }

    /*
     * Body JSON esperado:
     * {
     *   "titular": "João Silva",
     *   "numeroConta": "12345-6",
     *   "agencia": "0001",
     *   "tipo": "CORRENTE"
     * }
     */
    @POST
    public Response criarConta(Conta conta) {
        try {
            Conta criada = contaBean.criar(conta);
            return Response.status(Response.Status.CREATED)
                           .entity(criada).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response desativarConta(@PathParam("id") Long id) {
        try {
            contaBean.desativar(id);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("erro", e.getMessage())).build();
        }
    }

    // ================================================
    // Operações financeiras
    // ================================================

    @PUT
    @Path("/{id}/creditar")
    public Response creditar(@PathParam("id") Long id,
                             @QueryParam("valor") BigDecimal valor) {
        try {
            Conta c = contaBean.creditar(id, valor);
            return Response.ok(Map.of(
                "mensagem", "Crédito realizado",
                "novoSaldo", c.getSaldo()
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @PUT
    @Path("/{id}/debitar")
    public Response debitar(@PathParam("id") Long id,
                            @QueryParam("valor") BigDecimal valor) {
        try {
            Conta c = contaBean.debitar(id, valor);
            return Response.ok(Map.of(
                "mensagem", "Débito realizado",
                "novoSaldo", c.getSaldo()
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("erro", e.getMessage())).build();
        }
    }

    // ================================================
    // Boletos
    // ================================================

    /*
     * Body JSON esperado:
     * {
     *   "valor": "150.00",
     *   "vencimento": "2025-12-31",
     *   "beneficiario": "Empresa ABC"
     * }
     */
    @POST
    @Path("/{id}/boletos")
    public Response emitirBoleto(@PathParam("id") Long contaId,
                                  Map<String, String> body) {
        try {
            BigDecimal valor = new BigDecimal(body.get("valor"));
            LocalDate vencimento = LocalDate.parse(body.get("vencimento"));
            String beneficiario = body.get("beneficiario");

            Boleto boleto = boletoBean.emitir(
                contaId, valor, vencimento, beneficiario);

            return Response.status(Response.Status.CREATED)
                           .entity(boleto).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("erro", e.getMessage())).build();
        }
    }

    /*
     * Retorna HTTP 202 Accepted imediatamente.
     * O processamento ocorre em background via @Asynchronous.
     * O cliente precisa consultar o status depois.
     */
    @POST
    @Path("/{id}/boletos/{boletoId}/processar")
    public Response processarBoleto(
            @PathParam("id") Long contaId,
            @PathParam("boletoId") Long boletoId) {
        try {
            boletoBean.processarBoleto(boletoId);
            return Response.accepted(Map.of(
                "mensagem", "Boleto enviado para processamento",
                "boletoId", boletoId,
                "status", "PROCESSANDO"
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{id}/boletos")
    public Response listarBoletos(@PathParam("id") Long contaId) {
        List<Boleto> boletos = boletoBean.listarPorConta(contaId);
        return Response.ok(boletos).build();
    }

    @GET
    @Path("/{id}/boletos/{boletoId}")
    public Response buscarBoleto(@PathParam("id") Long contaId,
                                  @PathParam("boletoId") Long boletoId) {
        return boletoBean.buscarPorId(boletoId)
            .map(b -> Response.ok(b).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
    
 // ================================================
 // Publicar boleto na fila JMS
 // ================================================

 /*
  * Publica o ID do boleto na fila BoletoQueue.
  * O MDB (BoletoListenerBean) vai consumir automaticamente.
  *
  * Diferença entre este endpoint e o /processar:
  *   /processar → chama @Asynchronous diretamente (mesmo servidor)
  *   /publicar  → publica na fila JMS → MDB consome (desacoplado)
  *
  * Em sistemas reais o /publicar é mais robusto porque:
  *   - A mensagem fica na fila mesmo se o servidor reiniciar
  *   - Permite múltiplos consumidores
  *   - Desacopla produtor do consumidor
  */
 @POST
 @Path("/{id}/boletos/{boletoId}/publicar")
 public Response publicarNaFila(
         @PathParam("id") Long contaId,
         @PathParam("boletoId") Long boletoId) {
     try {
         // Verifica se o boleto existe antes de publicar
         return boletoBean.buscarPorId(boletoId).map(boleto -> {
             jmsProducerBean.publicarBoleto(boletoId);
             return Response.accepted(Map.of(
                 "mensagem", "Boleto publicado na fila JMS",
                 "boletoId", boletoId,
                 "fila", "BoletoQueue"
             )).build();
         }).orElse(
             Response.status(Response.Status.NOT_FOUND)
                 .entity(Map.of("erro", "Boleto não encontrado: "
                                + boletoId))
                 .build()
         );
     } catch (Exception e) {
         return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
             .entity(Map.of("erro", e.getMessage())).build();
     }
 }
}