package com.quickcare.backend.notificacao;

import com.quickcare.backend.notificacao.dto.NaoLidasResponse;
import com.quickcare.backend.notificacao.dto.NotificacaoResponse;
import com.quickcare.backend.notificacao.exception.NotificacaoNaoEncontradaException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    private static final Long USUARIO_ID = 1L;

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificacaoService service;

    private final Usuario usuario = Usuario.builder().id(USUARIO_ID).nome("Ana").email("ana@quickcare.dev").build();

    @Test
    void gravaNoPerfilEPublicaParaEntregaSemEmail() {
        when(notificacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.notificar(usuario, TipoPerfil.PACIENTE, TipoNotificacao.PROPOSTA_RECEBIDA,
                "Nova proposta", "Mensagem", "/patient/solicitacoes/50", false);

        ArgumentCaptor<NotificacaoCriada> evento = ArgumentCaptor.forClass(NotificacaoCriada.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().usuarioId()).isEqualTo(USUARIO_ID);
        assertThat(evento.getValue().notificacao().perfil()).isEqualTo(TipoPerfil.PACIENTE);
        assertThat(evento.getValue().notificacao().lida()).isFalse();
        assertThat(evento.getValue().email()).isNull();
    }

    @Test
    void pedeEmailQuandoOEventoExige() {
        when(notificacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.notificar(usuario, TipoPerfil.PROFISSIONAL, TipoNotificacao.CADASTRO_APROVADO,
                "Cadastro aprovado", "Mensagem", "/professional", true);

        ArgumentCaptor<NotificacaoCriada> evento = ArgumentCaptor.forClass(NotificacaoCriada.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().email()).isEqualTo(new NotificacaoCriada.Email("ana@quickcare.dev", "Ana"));
    }

    @Test
    void limitaQuantasListaDeUmaVez() {
        when(notificacaoRepository.findByUsuarioIdAndPerfilOrderByCriadoEmDescIdDesc(eq(USUARIO_ID), eq(TipoPerfil.PACIENTE), any()))
                .thenReturn(List.of());

        service.listar(USUARIO_ID, TipoPerfil.PACIENTE, 1000);

        verify(notificacaoRepository).findByUsuarioIdAndPerfilOrderByCriadoEmDescIdDesc(
                USUARIO_ID, TipoPerfil.PACIENTE, PageRequest.of(0, 100));
    }

    @Test
    void marcaComoLidaSoANotificacaoDoProprioUsuario() {
        Notificacao notificacao = Notificacao.builder().id(7L).usuario(usuario).perfil(TipoPerfil.PACIENTE)
                .tipo(TipoNotificacao.PROPOSTA_RECEBIDA).titulo("t").mensagem("m").build();
        when(notificacaoRepository.findByIdAndUsuarioId(7L, USUARIO_ID)).thenReturn(Optional.of(notificacao));
        when(notificacaoRepository.findByIdAndUsuarioId(7L, 2L)).thenReturn(Optional.empty());

        NotificacaoResponse lida = service.marcarComoLida(USUARIO_ID, 7L);

        assertThat(lida.lida()).isTrue();
        assertThatThrownBy(() -> service.marcarComoLida(2L, 7L)).isInstanceOf(NotificacaoNaoEncontradaException.class);
    }

    @Test
    void marcarTodasDevolveAsNaoLidasDeCadaPerfil() {
        when(notificacaoRepository.countByUsuarioIdAndPerfilAndLidaEmIsNull(USUARIO_ID, TipoPerfil.PACIENTE)).thenReturn(0L);
        when(notificacaoRepository.countByUsuarioIdAndPerfilAndLidaEmIsNull(USUARIO_ID, TipoPerfil.PROFISSIONAL)).thenReturn(3L);

        NaoLidasResponse naoLidas = service.marcarTodasComoLidas(USUARIO_ID, TipoPerfil.PACIENTE);

        verify(notificacaoRepository).marcarTodasComoLidas(eq(USUARIO_ID), eq(TipoPerfil.PACIENTE), any());
        assertThat(naoLidas).isEqualTo(new NaoLidasResponse(0, 3));
    }

}
