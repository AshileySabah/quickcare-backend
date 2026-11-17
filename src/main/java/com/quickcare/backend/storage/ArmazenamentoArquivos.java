package com.quickcare.backend.storage;

import java.time.Duration;

public interface ArmazenamentoArquivos {

    void enviar(String chave, byte[] conteudo, String contentType);

    void excluir(String chave);

    byte[] baixar(String chave);

    String urlAssinada(String chave, Duration expiracao);
}
