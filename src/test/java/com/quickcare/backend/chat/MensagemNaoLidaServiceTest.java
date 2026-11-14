package com.quickcare.backend.chat;

import com.quickcare.backend.chat.exception.ConversaAcessoNegadoException;
import com.quickcare.backend.common.EmailService;
import com.quickcare.backend.notificacao.NotificacaoService;
import com.quickcare.backend.notificacao.TipoNotificacao;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MensagemNaoLidaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-14T15:00:00Z");

    @Mock private MensagemNaoLidaRepository repository;
    @Mock private ChatPropostaRepository chatPropostaRepository;
    @Mock private ConversaService conversaService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotificacaoService notificacaoService;
    @Mock private EmailService emailService;

    private MensagemNaoLidaService service;
    private final Usuario paciente = Usuario.builder().id(1L).nome("Helena Prado").email("helena@quickcare.dev").build();
    private final Usuario enfermeira = Usuario.builder().id(2L).nome("Rafaela Nunes").email("rafaela@quickcare.dev").build();
    private final Usuario cuidador = Usuario.builder().id(3L).nome("Otávio Reis").email("otavio@quickcare.dev").build();

    @BeforeEach
    void setUp() {
        service = new MensagemNaoLidaService(repository, chatPropostaRepository, conversaService, usuarioRepository,
                notificacaoService, emailService, Clock.fixed(AGORA, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:4200");
    }

    private Mensagem mensagem(Long remetenteId, Long profissionalUsuarioId) {
        Mensagem mensagem = new Mensagem(remetenteId, 10L, profissionalUsuarioId, "oi");
        ReflectionTestUtils.setField(mensagem, "id", 99L);
        return mensagem;
    }

    @Test
    void mensagemNoGrupoFicaNaoLidaParaOsOutrosEAvisaNoSinoSoNaPrimeira() {
        when(chatPropostaRepository.usuarioIdDoPaciente(10L)).thenReturn(Optional.of(1L));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(enfermeira));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(repository.existeNoCanal(1L, 10L, null)).thenReturn(false);
        when(repository.existeNoCanal(3L, 10L, null)).thenReturn(true);

        service.registrar(mensagem(2L, null), List.of(1L, 2L, 3L));

        ArgumentCaptor<MensagemNaoLida> salvas = ArgumentCaptor.forClass(MensagemNaoLida.class);
        verify(repository, org.mockito.Mockito.times(2)).save(salvas.capture());
        assertThat(salvas.getAllValues()).extracting(MensagemNaoLida::getUsuarioId).containsExactly(1L, 3L);
        verify(notificacaoService).notificar(eq(paciente), eq(TipoPerfil.PACIENTE), eq(TipoNotificacao.MENSAGEM_RECEBIDA),
                eq("Nova mensagem de Rafaela Nunes"), anyString(), eq("/patient/mensagens/10/grupo"), eq(false));
        verify(notificacaoService, never()).notificar(eq(cuidador), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void abrirOCanalMarcaComoLidoSoDepoisDeConferirOAcesso() {
        when(repository.contarPorCanal(1L)).thenReturn(List.of());

        service.marcarComoLido(1L, 10L, 2L);

        verify(conversaService).acessoDe(10L, 2L, 1L);
        verify(repository).marcarCanalComoLido(1L, 10L, 2L);
    }

    @Test
    void quemNaoParticipaNaoMarcaNadaComoLido() {
        when(conversaService.acessoDe(10L, 2L, 9L)).thenThrow(new ConversaAcessoNegadoException());

        assertThatThrownBy(() -> service.marcarComoLido(9L, 10L, 2L)).isInstanceOf(ConversaAcessoNegadoException.class);
        verify(repository, never()).marcarCanalComoLido(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void depoisDeUmaHoraSemLerMandaUmEmailComOTotalENaoRepete() {
        MensagemNaoLida primeira = new MensagemNaoLida(mensagem(1L, 2L), 2L, AGORA.minus(Duration.ofMinutes(70)));
        MensagemNaoLida segunda = new MensagemNaoLida(mensagem(1L, 2L), 2L, AGORA.minus(Duration.ofMinutes(65)));
        when(repository.usuariosComAvisoPendente(AGORA.minus(Duration.ofHours(1)))).thenReturn(List.of(2L));
        when(repository.findByUsuarioIdAndEmailEnviadoEmIsNull(2L)).thenReturn(List.of(primeira, segunda));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(enfermeira));
        when(chatPropostaRepository.usuarioIdDoPaciente(10L)).thenReturn(Optional.of(1L));

        service.avisarPorEmail();

        ArgumentCaptor<Map<String, Object>> variaveis = ArgumentCaptor.forClass(Map.class);
        verify(emailService).enviarEmailTemplate(eq("rafaela@quickcare.dev"), anyString(), eq("notificacao"), variaveis.capture());
        assertThat((String) variaveis.getValue().get("mensagem")).contains("2 mensagens não lidas");
        assertThat(variaveis.getValue().get("link")).isEqualTo("http://localhost:4200/professional/mensagens/10/2");
        assertThat(primeira.getEmailEnviadoEm()).isEqualTo(AGORA);
        assertThat(segunda.getEmailEnviadoEm()).isEqualTo(AGORA);
    }

}
