package com.quickcare.backend.notificacao;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.validacao.TipoEtapaValidacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificadorTest {

    @Mock
    private NotificacaoService notificacaoService;

    @InjectMocks
    private Notificador notificador;

    private Usuario usuarioPaciente;
    private Usuario usuarioProfissional;
    private Proposta proposta;

    @BeforeEach
    void setUp() {
        usuarioPaciente = Usuario.builder().id(1L).nome("Maria Paciente").build();
        usuarioProfissional = Usuario.builder().id(2L).nome("Carla Enfermeira").build();

        Solicitacao solicitacao = Solicitacao.builder()
                .id(50L)
                .paciente(Paciente.builder().id(10L).usuario(usuarioPaciente).build())
                .profissionais(new LinkedHashSet<>())
                .build();
        solicitacao.getProfissionais().add(SolicitacaoProfissional.builder()
                .categoria(CategoriaProfissional.ENFERMEIRO)
                .especialidades(new LinkedHashSet<>(List.of(
                        Especialidade.builder().id(1L).nome("Curativos").build(),
                        Especialidade.builder().id(2L).nome("Sondagem").build())))
                .build());
        solicitacao.getProfissionais().add(SolicitacaoProfissional.builder()
                .categoria(CategoriaProfissional.CUIDADOR)
                .especialidades(new LinkedHashSet<>(List.of(Especialidade.builder().id(3L).nome("Higiene").build())))
                .build());

        proposta = Proposta.builder()
                .id(100L)
                .solicitacao(solicitacao)
                .profissional(Profissional.builder().id(20L).usuario(usuarioProfissional).build())
                .status(StatusProposta.PENDENTE)
                .build();
    }

    @Test
    void descreveOServicoPelasEspecialidadesPedidas() {
        assertThat(Notificador.servico(proposta)).isEqualTo("Curativos, Sondagem e Higiene");
    }

    @Test
    void pacienteFicaSabendoQuemEnviouAProposta() {
        notificador.propostaRecebida(proposta);

        ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
        verify(notificacaoService).notificar(eq(usuarioPaciente), eq(TipoPerfil.PACIENTE),
                eq(TipoNotificacao.PROPOSTA_RECEBIDA), anyString(), mensagem.capture(),
                eq("/patient/solicitacoes/50?destaque=proposta-100"), eq(false));
        assertThat(mensagem.getValue()).startsWith("Carla Enfermeira enviou uma proposta");
    }

    @Test
    void profissionalNaoVeONomeDoPaciente() {
        notificador.propostaRecusada(proposta, false);
        notificador.propostaAceita(proposta);

        ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
        verify(notificacaoService, times(2)).notificar(eq(usuarioProfissional),
                eq(TipoPerfil.PROFISSIONAL), any(), anyString(), mensagem.capture(),
                any(), eq(false));
        assertThat(mensagem.getAllValues()).noneMatch(texto -> texto.contains("Maria"));
        assertThat(mensagem.getAllValues().get(0)).contains("você pode reenviar");
    }

    @Test
    void oLinkDizQualItemDaTelaDestacar() {
        notificador.propostaRecusada(proposta, false);
        notificador.propostaRecusada(proposta, true);

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notificacaoService, times(2)).notificar(eq(usuarioProfissional), eq(TipoPerfil.PROFISSIONAL),
                eq(TipoNotificacao.PROPOSTA_RECUSADA), anyString(), anyString(), link.capture(), eq(false));
        // Recusada pelo paciente: o card de reenvio. Pelas vagas lotadas: a solicitação.
        assertThat(link.getAllValues()).containsExactly(
                "/professional/solicitacoes/50?destaque=proposta-100",
                "/professional/solicitacoes/50?destaque=solicitacao-50");
    }

    @Test
    void etapaReprovadaVaiPorEmailComAJustificativa() {
        notificador.etapaReprovada(usuarioProfissional, TipoPerfil.PROFISSIONAL, TipoEtapaValidacao.CNPJ, "Documento ilegível");

        ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
        verify(notificacaoService).notificar(eq(usuarioProfissional), eq(TipoPerfil.PROFISSIONAL),
                eq(TipoNotificacao.ETAPA_REPROVADA), anyString(), mensagem.capture(),
                eq("/professional/aguardando-validacao?destaque=validacao"), eq(true));
        assertThat(mensagem.getValue())
                .isEqualTo("A etapa CNPJ foi reprovada: Documento ilegível. Corrija os dados e reenvie o documento.");
    }

}
