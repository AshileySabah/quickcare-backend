package com.quickcare.backend.notificacao;

import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.validacao.TipoEtapaValidacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Os textos de cada notificação, a partir do evento de domínio. O paciente só
 * é identificado ao profissional como "o paciente" (o nome aparece na tela
 * depois do aceite); o profissional sempre aparece pelo nome. O link leva à
 * tela e diz qual item dela destacar.
 */
@Component
@RequiredArgsConstructor
public class Notificador {

    private static final int TAMANHO_MAXIMO_MENSAGEM = 500;
    /** Mesmo nome que o front lê (HIGHLIGHT_PARAM). */
    private static final String PARAMETRO_DESTAQUE = "destaque";

    private final NotificacaoService notificacaoService;

    // ------------------------------------------------------ para o paciente

    public void propostaRecebida(Proposta proposta) {
        paraPaciente(proposta, Destaque.PROPOSTA, TipoNotificacao.PROPOSTA_RECEBIDA, "Nova proposta",
                nomeDoProfissional(proposta) + " enviou uma proposta para a sua solicitação de " + servico(proposta) + ".");
    }

    public void propostaAlterada(Proposta proposta, boolean estavaAceita) {
        if (estavaAceita) {
            paraPaciente(proposta, Destaque.PROPOSTA, TipoNotificacao.PROPOSTA_AGUARDA_REACEITE, "Proposta alterada: aceite de novo",
                    nomeDoProfissional(proposta) + " alterou a proposta que você tinha aceitado para "
                            + servico(proposta) + ". Aceite de novo para confirmar, ou escolha outra.");
        } else {
            paraPaciente(proposta, Destaque.PROPOSTA, TipoNotificacao.PROPOSTA_ALTERADA, "Proposta alterada",
                    nomeDoProfissional(proposta) + " alterou a proposta para a sua solicitação de " + servico(proposta) + ".");
        }
    }

    public void propostaCancelada(Proposta proposta, boolean estavaAceita) {
        String mensagem = estavaAceita
                ? nomeDoProfissional(proposta) + " cancelou a proposta que você tinha aceitado para "
                        + servico(proposta) + ". A vaga voltou a receber propostas."
                : nomeDoProfissional(proposta) + " cancelou a proposta para a sua solicitação de " + servico(proposta) + ".";
        paraPaciente(proposta, Destaque.SOLICITACAO, TipoNotificacao.PROPOSTA_CANCELADA, "Proposta cancelada", mensagem);
    }

    // -------------------------------------------------- para o profissional

    public void propostaAceita(Proposta proposta) {
        paraProfissional(proposta, Destaque.PROPOSTA, TipoNotificacao.PROPOSTA_ACEITA, "Proposta aceita",
                "O paciente aceitou a sua proposta para " + servico(proposta) + ".");
    }

    /** Automática: as vagas que ele atenderia foram preenchidas por outras propostas. */
    public void propostaRecusada(Proposta proposta, boolean automatica) {
        String mensagem = automatica
                ? "A sua proposta para " + servico(proposta) + " foi recusada: as vagas foram preenchidas por outros profissionais."
                : "O paciente recusou a sua proposta para " + servico(proposta)
                        + ". Enquanto a solicitação receber propostas, você pode reenviar.";
        // Recusada pelo paciente, a tela mostra o formulário de reenvio (com a
        // proposta anterior); recusada porque as vagas lotaram, só a solicitação.
        paraProfissional(proposta, automatica ? Destaque.SOLICITACAO : Destaque.PROPOSTA,
                TipoNotificacao.PROPOSTA_RECUSADA, "Proposta recusada", mensagem);
    }

    /** As que estavam aceitas voltaram a aguardar o aceite; as pendentes só precisam ser conferidas. */
    public void solicitacaoAlterada(Collection<Proposta> aguardandoReaceite, Collection<Proposta> pendentes) {
        aguardandoReaceite.forEach(proposta -> paraProfissional(proposta, Destaque.PROPOSTA, TipoNotificacao.PROPOSTA_AGUARDA_REACEITE,
                "Solicitação alterada: aguarde novo aceite",
                "O paciente alterou a solicitação de " + servico(proposta)
                        + " depois de aceitar a sua proposta. Revise a proposta: ela aguarda um novo aceite."));
        pendentes.forEach(proposta -> paraProfissional(proposta, Destaque.SOLICITACAO, TipoNotificacao.SOLICITACAO_ALTERADA,
                "Solicitação alterada",
                "O paciente alterou a solicitação de " + servico(proposta) + ". Confira se a sua proposta ainda atende."));
    }

    public void solicitacaoCancelada(Collection<Proposta> afetadas) {
        afetadas.forEach(proposta -> paraProfissional(proposta, Destaque.SOLICITACAO, TipoNotificacao.SOLICITACAO_CANCELADA,
                "Solicitação cancelada",
                "O paciente cancelou a solicitação de " + servico(proposta) + "."));
    }

    public void solicitacaoConcluida(Collection<Proposta> aceitas) {
        aceitas.forEach(proposta -> paraProfissional(proposta, Destaque.SOLICITACAO, TipoNotificacao.SOLICITACAO_CONCLUIDA,
                "Atendimento concluído",
                "O paciente marcou como concluída a solicitação de " + servico(proposta) + "."));
    }

    // ------------------------------------------------------------ validação

    public void cadastroAprovado(Usuario usuario, TipoPerfil perfil) {
        String mensagem = perfil == TipoPerfil.PACIENTE
                ? "Seu cadastro de paciente foi aprovado. Você já pode criar solicitações."
                : "Seu cadastro de profissional foi aprovado. Você já pode enviar propostas.";
        notificacaoService.notificar(usuario, perfil, TipoNotificacao.CADASTRO_APROVADO,
                "Cadastro aprovado", mensagem, inicioDo(perfil), true);
    }

    public void etapaReprovada(Usuario usuario, TipoPerfil perfil, TipoEtapaValidacao etapa, String justificativa) {
        notificacaoService.notificar(usuario, perfil, TipoNotificacao.ETAPA_REPROVADA,
                "Cadastro precisa de ajustes",
                limitar("A etapa " + etapa.getDescricao() + " foi reprovada: " + comPontoFinal(justificativa)
                        + " Corrija os dados e reenvie o documento."),
                validacaoDo(perfil), true);
    }

    // ---------------------------------------------------------------- apoio

    /** O item que a tela destaca ao abrir a notificação ({@code ?destaque=} no link). */
    private enum Destaque {
        PROPOSTA,
        SOLICITACAO
    }

    private void paraPaciente(Proposta proposta, Destaque destaque, TipoNotificacao tipo, String titulo, String mensagem) {
        Solicitacao solicitacao = proposta.getSolicitacao();
        notificacaoService.notificar(solicitacao.getPaciente().getUsuario(), TipoPerfil.PACIENTE, tipo, titulo,
                limitar(mensagem), linkDaSolicitacao("/patient", proposta, destaque), false);
    }

    private void paraProfissional(Proposta proposta, Destaque destaque, TipoNotificacao tipo, String titulo, String mensagem) {
        notificacaoService.notificar(proposta.getProfissional().getUsuario(), TipoPerfil.PROFISSIONAL, tipo, titulo,
                limitar(mensagem), linkDaSolicitacao("/professional", proposta, destaque), false);
    }

    private static String linkDaSolicitacao(String area, Proposta proposta, Destaque destaque) {
        Long solicitacaoId = proposta.getSolicitacao().getId();
        String alvo = destaque == Destaque.PROPOSTA ? "proposta-" + proposta.getId() : "solicitacao-" + solicitacaoId;
        return area + "/solicitacoes/" + solicitacaoId + "?" + PARAMETRO_DESTAQUE + "=" + alvo;
    }

    private static String inicioDo(TipoPerfil perfil) {
        return perfil == TipoPerfil.PACIENTE ? "/patient" : "/professional";
    }

    /** Onde fica a caixa da validação: na home do paciente; para o profissional, na tela de espera. */
    private static String validacaoDo(TipoPerfil perfil) {
        String tela = perfil == TipoPerfil.PACIENTE ? "/patient" : "/professional/aguardando-validacao";
        return tela + "?" + PARAMETRO_DESTAQUE + "=validacao";
    }

    private static String nomeDoProfissional(Proposta proposta) {
        return proposta.getProfissional().getUsuario().getNome();
    }

    /** As especialidades pedidas, ex.: "Curativos e Sondagem". */
    static String servico(Proposta proposta) {
        Set<String> nomes = new LinkedHashSet<>();
        proposta.getSolicitacao().getProfissionais().forEach(linha ->
                linha.getEspecialidades().stream().map(Especialidade::getNome).forEach(nomes::add));

        List<String> lista = List.copyOf(nomes);
        if (lista.isEmpty()) {
            return "atendimento";
        }
        if (lista.size() == 1) {
            return lista.get(0);
        }
        return String.join(", ", lista.subList(0, lista.size() - 1)) + " e " + lista.get(lista.size() - 1);
    }

    private static String comPontoFinal(String texto) {
        String limpo = texto.trim();
        return limpo.matches(".*[.!?]$") ? limpo : limpo + ".";
    }

    private static String limitar(String mensagem) {
        return mensagem.length() <= TAMANHO_MAXIMO_MENSAGEM
                ? mensagem
                : mensagem.substring(0, TAMANHO_MAXIMO_MENSAGEM - 1) + "…";
    }

}
