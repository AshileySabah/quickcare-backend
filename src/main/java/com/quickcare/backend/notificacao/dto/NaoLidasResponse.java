package com.quickcare.backend.notificacao.dto;

/** Não lidas de cada perfil: a do ativo vai no sino, a do outro no seletor de perfil. */
public record NaoLidasResponse(long paciente, long profissional) {
}
