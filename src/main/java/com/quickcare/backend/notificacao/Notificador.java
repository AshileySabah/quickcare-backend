package com.quickcare.backend.notificacao;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.ocorrencia.Bloqueio;
import com.quickcare.backend.ocorrencia.MotivoOcorrencia;
import com.quickcare.backend.ocorrencia.Ocorrencia;
import com.quickcare.backend.ocorrencia.RecursoOcorrencia;
import com.quickcare.backend.ocorrencia.RegrasBloqueio;
import com.quickcare.backend.problema.DecisaoProblema;
import com.quickcare.backend.problema.MotivoProblema;
import com.quickcare.backend.problema.ProblemaAtendimento;
import com.quickcare.backend.proposta.AcaoAdminProposta;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.solicitacao.AcaoAdminSolicitacao;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.validacao.TipoEtapaValidacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class Notificador {

    private static final int TAMANHO_MAXIMO_MENSAGEM = 500;
    private static final String PARAMETRO_DESTAQUE = "destaque";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificacaoService notificacaoService;

    public void propostaRecebida(Proposta proposta) {
        paraPaciente(proposta, Destaque.PROPOSTA, TipoNotificacao.PROPOSTA_RECEBIDA, "Nova proposta",
                nomeDoProfissional(proposta) + " enviou uma proposta para a sua solicitação de " + servico(proposta) + ".");
    }

    public void propostaAlterada(Proposta proposta, boolean estavaAceita) {
        if (estavaAceita) {
            paraPaciente(proposta, Destaque.PROPOSTA, TipoNotificacao.PROPOSTA_AGUARDA_REACEITE, "Proposta alterada: aceite de novo",
                    nomeDoProfissional(proposta) + " alterou a proposta que você tinha aceitado para "
                            + servico(proposta) + ". Aceite de novo para confirmar, ou escolha outra.", true);
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

    public void propostaAceita(Proposta proposta) {
        paraProfissional(proposta, Destaque.PROPOSTA, TipoNotificacao.PROPOSTA_ACEITA, "Proposta aceita",
                "O paciente aceitou a sua proposta para " + servico(proposta) + ".");
    }

    public void propostaRecusada(Proposta proposta, boolean automatica) {
        String mensagem = automatica
                ? "A sua proposta para " + servico(proposta) + " foi recusada: as vagas foram preenchidas por outros profissionais."
                : "O paciente recusou a sua proposta para " + servico(proposta)
                        + ". Enquanto a solicitação receber propostas, você pode reenviar.";
        paraProfissional(proposta, automatica ? Destaque.SOLICITACAO : Destaque.PROPOSTA,
                TipoNotificacao.PROPOSTA_RECUSADA, "Proposta recusada", mensagem);
    }

    public void solicitacaoAlterada(Collection<Proposta> aguardandoReaceite, Collection<Proposta> pendentes) {
        aguardandoReaceite.forEach(proposta -> paraProfissional(proposta, Destaque.PROPOSTA, TipoNotificacao.PROPOSTA_AGUARDA_REACEITE,
                "Solicitação alterada: reaceite ou recuse",
                "O paciente alterou a solicitação de " + servico(proposta)
                        + " depois de aceitar a sua proposta. Reaceite ou recuse a nova versão (recusar não gera penalidade).",
                true));
        pendentes.forEach(proposta -> paraProfissional(proposta, Destaque.SOLICITACAO, TipoNotificacao.SOLICITACAO_ALTERADA,
                "Solicitação alterada",
                "O paciente alterou a solicitação de " + servico(proposta) + ". Confira se a sua proposta ainda atende."));
    }

    public void solicitacaoCancelada(Collection<Proposta> afetadas) {
        afetadas.forEach(proposta -> paraProfissional(proposta, Destaque.SOLICITACAO, TipoNotificacao.SOLICITACAO_CANCELADA,
                "Solicitação cancelada",
                "O paciente cancelou a solicitação de " + servico(proposta) + "."));
    }

    public void reaceiteRespondido(Proposta proposta, boolean reaceitou) {
        if (reaceitou) {
            paraPaciente(proposta, Destaque.PROPOSTA, TipoNotificacao.REACEITE_ACEITO, "Profissional reaceitou",
                    nomeDoProfissional(proposta) + " reaceitou a nova versão da sua solicitação de " + servico(proposta) + ".");
        } else {
            paraPaciente(proposta, Destaque.SOLICITACAO, TipoNotificacao.REACEITE_RECUSADO, "Profissional recusou a nova versão",
                    nomeDoProfissional(proposta) + " recusou a nova versão da sua solicitação de " + servico(proposta)
                            + ". A vaga voltou a receber propostas.");
        }
    }

    public void substituido(Proposta proposta) {
        paraProfissional(proposta, Destaque.SOLICITACAO, TipoNotificacao.ATENDIMENTO_SUBSTITUIDO, "Vaga preenchida por outro profissional",
                "Enquanto aguardava o seu reaceite, o paciente escolheu outro profissional para a vaga de "
                        + servico(proposta) + ". Não há penalidade para você.", true);
    }

    public void edicaoIncompativel(Proposta proposta) {
        paraProfissional(proposta, Destaque.SOLICITACAO, TipoNotificacao.ATENDIMENTO_CANCELADO, "Atendimento cancelado",
                "A nova versão da solicitação de " + servico(proposta)
                        + " não tem mais vaga para o seu perfil, então o atendimento foi cancelado. Não há penalidade para você.",
                true);
    }

    public void semReaceite(Atendimento atendimento) {
        Proposta proposta = atendimento.getProposta();
        paraProfissional(proposta, Destaque.SOLICITACAO, TipoNotificacao.ATENDIMENTO_CANCELADO, "Atendimento cancelado",
                "O atendimento de " + servico(proposta) + " foi cancelado: o horário de início chegou sem o seu reaceite.",
                true);
        paraPaciente(proposta, Destaque.SOLICITACAO, TipoNotificacao.ATENDIMENTO_CANCELADO, "Atendimento cancelado",
                nomeDoProfissional(proposta) + " não reaceitou a tempo, então o atendimento de " + servico(proposta)
                        + " foi cancelado e a vaga voltou a receber propostas.", true);
    }

    public void remarcacaoSolicitada(Atendimento atendimento) {
        Proposta proposta = atendimento.getProposta();
        String periodo = periodo(atendimento.getRemarcacaoInicio(), atendimento.getRemarcacaoTermino());
        if (atendimento.getRemarcacaoPor() == LadoAtendimento.PACIENTE) {
            paraProfissional(proposta, Destaque.PROPOSTA, TipoNotificacao.REMARCACAO_SOLICITADA, "Pedido de remarcação",
                    "O paciente pediu para remarcar o atendimento de " + servico(proposta) + " para " + periodo
                            + ". Aceite ou recuse.", true);
        } else {
            paraPaciente(proposta, Destaque.PROPOSTA, TipoNotificacao.REMARCACAO_SOLICITADA, "Pedido de remarcação",
                    nomeDoProfissional(proposta) + " pediu para remarcar o atendimento de " + servico(proposta) + " para "
                            + periodo + ". Aceite ou recuse.", true);
        }
    }

    public void remarcacaoRespondida(Atendimento atendimento, LadoAtendimento pedidaPor, boolean aceita) {
        Proposta proposta = atendimento.getProposta();
        String quemRespondeu = pedidaPor == LadoAtendimento.PACIENTE ? nomeDoProfissional(proposta) : "O paciente";
        String mensagem = aceita
                ? quemRespondeu + " aceitou a remarcação. O atendimento de " + servico(proposta) + " agora é "
                        + periodo(atendimento.getInicio(), atendimento.getTermino()) + "."
                : quemRespondeu + " recusou a remarcação. O atendimento de " + servico(proposta) + " continua "
                        + periodo(atendimento.getInicio(), atendimento.getTermino()) + ".";
        TipoNotificacao tipo = aceita ? TipoNotificacao.REMARCACAO_ACEITA : TipoNotificacao.REMARCACAO_RECUSADA;
        String titulo = aceita ? "Remarcação aceita" : "Remarcação recusada";

        if (pedidaPor == LadoAtendimento.PACIENTE) {
            paraPaciente(proposta, Destaque.PROPOSTA, tipo, titulo, mensagem, true);
        } else {
            paraProfissional(proposta, Destaque.PROPOSTA, tipo, titulo, mensagem, true);
        }
    }

    public void atendimentoCancelado(Atendimento atendimento, LadoAtendimento canceladoPor) {
        Proposta proposta = atendimento.getProposta();
        String quando = periodo(atendimento.getInicio(), atendimento.getTermino());
        if (canceladoPor == LadoAtendimento.PACIENTE) {
            paraProfissional(proposta, Destaque.SOLICITACAO, TipoNotificacao.ATENDIMENTO_CANCELADO, "Atendimento cancelado",
                    "O paciente cancelou o atendimento de " + servico(proposta) + " marcado para " + quando + ".", true);
        } else {
            paraPaciente(proposta, Destaque.SOLICITACAO, TipoNotificacao.ATENDIMENTO_CANCELADO, "Atendimento cancelado",
                    nomeDoProfissional(proposta) + " cancelou o atendimento de " + servico(proposta) + " marcado para "
                            + quando + ". A vaga voltou a receber propostas.", true);
        }
    }

    public enum Lembrete {
        VESPERA,
        TRINTA_MINUTOS,
        INICIO
    }

    public void lembrete(Atendimento atendimento, Lembrete lembrete) {
        Proposta proposta = atendimento.getProposta();
        String hora = HORA.format(atendimento.getInicio().atZone(FUSO));
        String mensagem = switch (lembrete) {
            case VESPERA -> "O atendimento de " + servico(proposta) + " é " + quando(atendimento.getInicio()) + ".";
            case TRINTA_MINUTOS -> "O atendimento de " + servico(proposta) + " começa às " + hora + ".";
            case INICIO -> "O atendimento de " + servico(proposta) + " começou agora (" + hora + ").";
        };
        String titulo = lembrete == Lembrete.INICIO ? "Atendimento começando" : "Lembrete de atendimento";

        paraPaciente(proposta, Destaque.PROPOSTA, TipoNotificacao.LEMBRETE_ATENDIMENTO, titulo,
                mensagem + " Profissional: " + nomeDoProfissional(proposta) + ".", true);
        paraProfissional(proposta, Destaque.PROPOSTA, TipoNotificacao.LEMBRETE_ATENDIMENTO, titulo, mensagem, true);
    }

    public void atendimentoFinalizado(Atendimento atendimento) {
        Proposta proposta = atendimento.getProposta();
        String mensagem = "O atendimento de " + servico(proposta) + " terminou.";
        paraPaciente(proposta, Destaque.PROPOSTA, TipoNotificacao.ATENDIMENTO_FINALIZADO, "Atendimento finalizado",
                mensagem + " Em breve você poderá avaliar " + nomeDoProfissional(proposta) + ".", true);
        paraProfissional(proposta, Destaque.PROPOSTA, TipoNotificacao.ATENDIMENTO_FINALIZADO, "Atendimento finalizado",
                mensagem + " Em breve você poderá avaliar o paciente.", true);
    }

    public void problemaRelatado(ProblemaAtendimento problema) {
        Proposta proposta = problema.getAtendimento().getProposta();
        String quem = nomeDoLado(proposta, problema.getRelatadoPor());
        String porque = problema.getMotivo() == MotivoProblema.COMBINADO
                ? " (combinado entre vocês)"
                : " porque você não compareceu";
        para(proposta, problema.ladoQueResponde(), Destaque.PROPOSTA, TipoNotificacao.PROBLEMA_RELATADO,
                "Atendimento relatado como não realizado",
                quem + " relatou que o atendimento de " + servico(proposta) + " não foi realizado" + porque
                        + ". Confirme ou conteste até " + quando(problema.getPrazoResposta()) + ".", true);
    }

    public void problemaConfirmado(ProblemaAtendimento problema) {
        Proposta proposta = problema.getAtendimento().getProposta();
        para(proposta, problema.getRelatadoPor(), Destaque.PROPOSTA, TipoNotificacao.PROBLEMA_CONFIRMADO,
                "Não realização confirmada",
                nomeDoLado(proposta, problema.ladoQueResponde()) + " confirmou que o atendimento de "
                        + servico(proposta) + " não foi realizado.", true);
    }

    public void problemaSemResposta(ProblemaAtendimento problema) {
        Proposta proposta = problema.getAtendimento().getProposta();
        String fim = "O prazo de resposta acabou: o atendimento de " + servico(proposta) + " ficou como não realizado.";
        para(proposta, problema.getRelatadoPor(), Destaque.PROPOSTA, TipoNotificacao.PROBLEMA_SEM_RESPOSTA,
                "Atendimento ficou como não realizado", fim, true);
        para(proposta, problema.ladoQueResponde(), Destaque.PROPOSTA, TipoNotificacao.PROBLEMA_SEM_RESPOSTA,
                "Atendimento ficou como não realizado",
                fim + (problema.ausente() != null ? " Isso conta uma ocorrência para você." : "")
                        + " Se discordar, ainda dá para contestar com o admin.", true);
    }

    public void problemaContestado(ProblemaAtendimento problema) {
        Proposta proposta = problema.getAtendimento().getProposta();
        para(proposta, problema.getRelatadoPor(), Destaque.PROPOSTA, TipoNotificacao.PROBLEMA_CONTESTADO,
                "Relato contestado",
                nomeDoLado(proposta, problema.ladoQueResponde()) + " contestou o seu relato sobre o atendimento de "
                        + servico(proposta) + ". Um administrador vai analisar e decidir.", true);
    }

    public void disputaDecidida(ProblemaAtendimento problema) {
        Proposta proposta = problema.getAtendimento().getProposta();
        String resultado = problema.getDecisao() == DecisaoProblema.REALIZADO ? "foi realizado" : "não foi realizado";
        String mensagem = "Um administrador analisou a disputa e decidiu que o atendimento de " + servico(proposta)
                + " " + resultado + ". Justificativa: " + comPontoFinal(problema.getDecisaoJustificativa());
        para(proposta, LadoAtendimento.PACIENTE, Destaque.PROPOSTA, TipoNotificacao.DISPUTA_DECIDIDA,
                "Disputa decidida", mensagem, true);
        para(proposta, LadoAtendimento.PROFISSIONAL, Destaque.PROPOSTA, TipoNotificacao.DISPUTA_DECIDIDA,
                "Disputa decidida", mensagem, true);
    }

    public void ocorrenciaRegistrada(Ocorrencia ocorrencia, int contando) {
        notificacaoService.notificar(ocorrencia.getUsuario(), ocorrencia.getPerfil(), TipoNotificacao.OCORRENCIA_REGISTRADA,
                "Você recebeu uma ocorrência",
                limitar(MOTIVOS_OCORRENCIA.get(ocorrencia.getMotivo()) + " Agora são " + contando + " de "
                        + RegrasBloqueio.LIMITE + " nos últimos " + RegrasBloqueio.JANELA_CONTAGEM.toDays()
                        + " dias; ao chegar a " + RegrasBloqueio.LIMITE + ", o perfil é bloqueado temporariamente."),
                ocorrenciasDo(ocorrencia.getPerfil()), false);
    }

    public void ocorrenciaAnulada(Ocorrencia ocorrencia) {
        notificacaoService.notificar(ocorrencia.getUsuario(), ocorrencia.getPerfil(), TipoNotificacao.OCORRENCIA_ANULADA,
                "Ocorrência anulada",
                limitar("Um administrador anulou uma ocorrência sua: " + comPontoFinal(ocorrencia.getJustificativaAnulacao())),
                ocorrenciasDo(ocorrencia.getPerfil()), true);
    }

    public void bloqueioAplicado(Bloqueio bloqueio) {
        String acao = bloqueio.getPerfil() == TipoPerfil.PACIENTE ? "criar solicitações" : "enviar propostas";
        String motivo = bloqueio.aplicadoPeloAdmin()
                ? "Um administrador bloqueou o seu perfil: " + comPontoFinal(bloqueio.getJustificativa()) + " Você"
                : "Você chegou a " + RegrasBloqueio.LIMITE + " ocorrências em " + RegrasBloqueio.JANELA_CONTAGEM.toDays()
                        + " dias e";
        notificacaoService.notificar(bloqueio.getUsuario(), bloqueio.getPerfil(), TipoNotificacao.BLOQUEIO_APLICADO,
                "Perfil bloqueado temporariamente",
                limitar(motivo + " não poderá " + acao + " até " + quando(bloqueio.getFim())
                        + ". O que já está em andamento continua normalmente."),
                ocorrenciasDo(bloqueio.getPerfil()), true);
    }

    public void bloqueioRemovido(Bloqueio bloqueio) {
        notificacaoService.notificar(bloqueio.getUsuario(), bloqueio.getPerfil(), TipoNotificacao.BLOQUEIO_REMOVIDO,
                "Bloqueio removido",
                limitar("Um administrador removeu o seu bloqueio: " + comPontoFinal(bloqueio.getJustificativaRemocao())),
                ocorrenciasDo(bloqueio.getPerfil()), true);
    }

    public void bloqueioDesfeito(Bloqueio bloqueio) {
        String acao = bloqueio.getPerfil() == TipoPerfil.PACIENTE ? "criar solicitações" : "enviar propostas";
        notificacaoService.notificar(bloqueio.getUsuario(), bloqueio.getPerfil(), TipoNotificacao.BLOQUEIO_REMOVIDO,
                "Bloqueio desfeito",
                "Uma das ocorrências que bloquearam o seu perfil foi anulada, e as que sobraram não chegam a "
                        + RegrasBloqueio.LIMITE + ". Você já pode " + acao + " de novo.",
                ocorrenciasDo(bloqueio.getPerfil()), true);
    }

    public void recursoDecidido(RecursoOcorrencia recurso) {
        Ocorrencia ocorrencia = recurso.getOcorrencia();
        String resultado = recurso.getStatus() == RecursoOcorrencia.Status.ANULADA
                ? "Um administrador aceitou o seu recurso e anulou a ocorrência: "
                : "Um administrador analisou o seu recurso e manteve a ocorrência: ";
        notificacaoService.notificar(ocorrencia.getUsuario(), ocorrencia.getPerfil(), TipoNotificacao.RECURSO_DECIDIDO,
                "Recurso de ocorrência decidido",
                limitar(resultado + comPontoFinal(recurso.getJustificativaDecisao())),
                ocorrenciasDo(ocorrencia.getPerfil()), true);
    }

    public void propostaEncerradaPorBloqueio(Proposta proposta, LadoAtendimento quemBloqueou) {
        if (quemBloqueou == LadoAtendimento.PACIENTE) {
            paraProfissional(proposta, Destaque.SOLICITACAO, TipoNotificacao.PROPOSTA_RECUSADA, "Proposta recusada",
                    "O paciente recusou a sua proposta para " + servico(proposta) + ".");
        } else {
            propostaCancelada(proposta, false);
        }
    }

    public void vinculoEncerrado(Proposta proposta, boolean estavaAceita) {
        if (estavaAceita) {
            paraPaciente(proposta, Destaque.SOLICITACAO, TipoNotificacao.ATENDIMENTO_CANCELADO, "Atendimento cancelado",
                    "O atendimento de " + servico(proposta) + " com " + nomeDoProfissional(proposta)
                            + " foi cancelado, sem penalidade para você. A vaga voltou a receber propostas.", true);
            paraProfissional(proposta, Destaque.SOLICITACAO, TipoNotificacao.ATENDIMENTO_CANCELADO, "Atendimento cancelado",
                    "O atendimento de " + servico(proposta) + " foi cancelado, sem penalidade para você.", true);
        } else {
            paraProfissional(proposta, Destaque.SOLICITACAO, TipoNotificacao.PROPOSTA_RECUSADA, "Proposta encerrada",
                    "A sua proposta para " + servico(proposta) + " foi encerrada.");
        }
    }

    public void solicitacaoAlteradaPeloAdmin(Solicitacao solicitacao, AcaoAdminSolicitacao acao,
                                             Collection<Proposta> afetadas, String justificativa) {
        String motivo = " Justificativa: " + comPontoFinal(justificativa);
        String titulo = "Solicitação " + acao.getParticipio() + " pelo administrador";
        notificacaoService.notificar(solicitacao.getPaciente().getUsuario(), TipoPerfil.PACIENTE,
                TipoNotificacao.SOLICITACAO_ALTERADA_ADMIN, titulo,
                limitar("Um administrador " + acao.getVerbo() + " a sua solicitação." + motivo),
                "/patient/solicitacoes/" + solicitacao.getId() + "?" + PARAMETRO_DESTAQUE + "=solicitacao-" + solicitacao.getId(),
                true);
        afetadas.forEach(proposta -> paraProfissional(proposta, Destaque.SOLICITACAO, TipoNotificacao.SOLICITACAO_ALTERADA_ADMIN,
                titulo,
                "Um administrador alterou a solicitação de " + servico(proposta)
                        + ": a sua proposta ou atendimento foi encerrado, sem penalidade para você." + motivo, true));
    }

    public void propostaAlteradaPeloAdmin(Proposta proposta, AcaoAdminProposta acao, String justificativa) {
        String titulo = "Proposta " + acao.getParticipio() + " pelo administrador";
        String motivo = " Justificativa: " + comPontoFinal(justificativa);
        String efeito = switch (acao) {
            case RECUSAR -> " Não há penalidade para ninguém.";
            case CANCELAR -> " O atendimento foi cancelado, sem penalidade para ninguém.";
            case REATIVAR -> " Ela voltou a aguardar a resposta do paciente.";
        };
        paraPaciente(proposta, Destaque.PROPOSTA, TipoNotificacao.PROPOSTA_ALTERADA_ADMIN, titulo,
                "Um administrador " + acao.getVerbo() + " a proposta de " + nomeDoProfissional(proposta) + " para "
                        + servico(proposta) + "." + efeito + motivo, true);
        paraProfissional(proposta, Destaque.PROPOSTA, TipoNotificacao.PROPOSTA_ALTERADA_ADMIN, titulo,
                "Um administrador " + acao.getVerbo() + " a sua proposta para " + servico(proposta) + "." + efeito + motivo,
                true);
    }

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

    private enum Destaque {
        PROPOSTA,
        SOLICITACAO
    }

    private void paraPaciente(Proposta proposta, Destaque destaque, TipoNotificacao tipo, String titulo, String mensagem) {
        paraPaciente(proposta, destaque, tipo, titulo, mensagem, false);
    }

    private void paraPaciente(
            Proposta proposta, Destaque destaque, TipoNotificacao tipo, String titulo, String mensagem, boolean comEmail) {
        Solicitacao solicitacao = proposta.getSolicitacao();
        notificacaoService.notificar(solicitacao.getPaciente().getUsuario(), TipoPerfil.PACIENTE, tipo, titulo,
                limitar(mensagem), linkDaSolicitacao("/patient", proposta, destaque), comEmail);
    }

    private void paraProfissional(Proposta proposta, Destaque destaque, TipoNotificacao tipo, String titulo, String mensagem) {
        paraProfissional(proposta, destaque, tipo, titulo, mensagem, false);
    }

    private void para(Proposta proposta, LadoAtendimento lado, Destaque destaque, TipoNotificacao tipo, String titulo,
                      String mensagem, boolean comEmail) {
        if (lado == LadoAtendimento.PACIENTE) {
            paraPaciente(proposta, destaque, tipo, titulo, mensagem, comEmail);
        } else {
            paraProfissional(proposta, destaque, tipo, titulo, mensagem, comEmail);
        }
    }

    private static String nomeDoLado(Proposta proposta, LadoAtendimento lado) {
        return lado == LadoAtendimento.PACIENTE ? "O paciente" : nomeDoProfissional(proposta);
    }

    private static final Map<MotivoOcorrencia, String> MOTIVOS_OCORRENCIA = Map.of(
            MotivoOcorrencia.CANCELAMENTO_ATENDIMENTO, "Você cancelou um atendimento fora da janela de tolerância.",
            MotivoOcorrencia.ALTERACAO_TARDIA_SOLICITACAO,
            "Você alterou uma solicitação (ou trocou o profissional) a menos de 48h do atendimento.",
            MotivoOcorrencia.NAO_COMPARECIMENTO, "Um atendimento ficou registrado como não comparecimento seu.",
            MotivoOcorrencia.DECISAO_DISPUTA, "Um administrador decidiu uma disputa contra você.",
            MotivoOcorrencia.MANUAL, "Um administrador registrou uma ocorrência no seu perfil.");

    private static String ocorrenciasDo(TipoPerfil perfil) {
        return inicioDo(perfil) + "?" + PARAMETRO_DESTAQUE + "=ocorrencias";
    }

    private void paraProfissional(
            Proposta proposta, Destaque destaque, TipoNotificacao tipo, String titulo, String mensagem, boolean comEmail) {
        notificacaoService.notificar(proposta.getProfissional().getUsuario(), TipoPerfil.PROFISSIONAL, tipo, titulo,
                limitar(mensagem), linkDaSolicitacao("/professional", proposta, destaque), comEmail);
    }

    static String periodo(Instant inicio, Instant termino) {
        ZonedDateTime de = inicio.atZone(FUSO);
        ZonedDateTime ate = termino.atZone(FUSO);
        if (de.toLocalDate().equals(ate.toLocalDate())) {
            return DIA.format(de) + ", das " + HORA.format(de) + " às " + HORA.format(ate);
        }
        return "de " + DIA.format(de) + " às " + HORA.format(de) + " até " + DIA.format(ate) + " às " + HORA.format(ate);
    }

    private static String quando(Instant instante) {
        ZonedDateTime momento = instante.atZone(FUSO);
        LocalDate hoje = LocalDate.now(FUSO);
        String hora = " às " + HORA.format(momento);
        if (momento.toLocalDate().equals(hoje)) {
            return "hoje" + hora;
        }
        if (momento.toLocalDate().equals(hoje.plusDays(1))) {
            return "amanhã" + hora;
        }
        return "em " + DIA.format(momento) + hora;
    }

    private static String linkDaSolicitacao(String area, Proposta proposta, Destaque destaque) {
        Long solicitacaoId = proposta.getSolicitacao().getId();
        String alvo = destaque == Destaque.PROPOSTA ? "proposta-" + proposta.getId() : "solicitacao-" + solicitacaoId;
        return area + "/solicitacoes/" + solicitacaoId + "?" + PARAMETRO_DESTAQUE + "=" + alvo;
    }

    private static String inicioDo(TipoPerfil perfil) {
        return perfil == TipoPerfil.PACIENTE ? "/patient" : "/professional";
    }

    private static String validacaoDo(TipoPerfil perfil) {
        String tela = perfil == TipoPerfil.PACIENTE ? "/patient" : "/professional/aguardando-validacao";
        return tela + "?" + PARAMETRO_DESTAQUE + "=validacao";
    }

    private static String nomeDoProfissional(Proposta proposta) {
        return proposta.getProfissional().getUsuario().getNome();
    }

    public static String servico(Proposta proposta) {
        return servico(proposta.getSolicitacao());
    }

    public static String servico(Solicitacao solicitacao) {
        Set<String> nomes = new LinkedHashSet<>();
        solicitacao.getProfissionais().forEach(linha ->
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
