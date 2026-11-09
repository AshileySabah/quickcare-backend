package com.quickcare.backend.atuacao;

import com.quickcare.backend.atuacao.dto.AtuacaoRequest;
import com.quickcare.backend.cadastro.exception.EspecialidadeInvalidaException;
import com.quickcare.backend.cadastro.exception.RegistroProfissionalObrigatorioException;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.especialidade.EspecialidadeRepository;
import com.quickcare.backend.profissional.Profissional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MontagemAtuacoes {

    private final EspecialidadeRepository especialidadeRepository;

    public List<Atuacao> montar(Profissional profissional, List<AtuacaoRequest> pedidos, Collection<Atuacao> existentes) {
        List<Atuacao> todas = new ArrayList<>(existentes);
        List<Atuacao> novas = new ArrayList<>();

        for (AtuacaoRequest pedido : pedidos) {
            Set<Especialidade> especialidades = especialidadesDa(pedido.categoria(), pedido.especialidadeIds());
            exigirRegistro(pedido);

            Atuacao atuacao = Atuacao.builder()
                    .profissional(profissional)
                    .categoria(pedido.categoria())
                    .especialidades(especialidades)
                    .registroProfissional(Atuacao.exigeRegistro(pedido.categoria()) ? pedido.registroProfissional().trim() : null)
                    .registroProfissionalUf(Atuacao.exigeRegistro(pedido.categoria())
                            ? pedido.registroProfissionalUf().toUpperCase(Locale.ROOT)
                            : null)
                    .build();
            exigirSemRepeticao(atuacao, todas);
            todas.add(atuacao);
            novas.add(atuacao);
        }
        return novas;
    }

    public Set<Especialidade> especialidadesDa(CategoriaProfissional categoria, List<Long> especialidadeIds) {
        if (especialidadeIds == null || especialidadeIds.isEmpty()) {
            throw new EspecialidadeInvalidaException("Escolha pelo menos uma especialidade em cada atuação.");
        }
        Set<Long> ids = new HashSet<>(especialidadeIds);
        List<Especialidade> encontradas = especialidadeRepository.findAllByIdIn(List.copyOf(ids));
        if (encontradas.size() != ids.size()) {
            throw new EspecialidadeInvalidaException();
        }
        if (encontradas.stream().anyMatch(especialidade -> especialidade.getCategoria() != categoria)) {
            throw new EspecialidadeInvalidaException("As especialidades informadas não pertencem à categoria profissional selecionada");
        }
        if (categoria == CategoriaProfissional.OUTRO && ids.size() > 1) {
            throw new EspecialidadeInvalidaException(
                    "Cada atuação de \"Outros profissionais\" tem uma única especialidade, com o próprio registro.");
        }
        return new LinkedHashSet<>(encontradas);
    }

    private static void exigirRegistro(AtuacaoRequest pedido) {
        if (!Atuacao.exigeRegistro(pedido.categoria())) {
            return;
        }
        if (pedido.registroProfissional() == null || pedido.registroProfissional().isBlank()
                || pedido.registroProfissionalUf() == null || pedido.registroProfissionalUf().isBlank()) {
            throw new RegistroProfissionalObrigatorioException();
        }
    }

    private static void exigirSemRepeticao(Atuacao nova, Collection<Atuacao> outras) {
        for (Atuacao outra : outras) {
            if (!outra.ativa() || outra.getCategoria() != nova.getCategoria()) {
                continue;
            }
            if (nova.getCategoria() != CategoriaProfissional.OUTRO) {
                throw new EspecialidadeInvalidaException("Você já tem a atuação " + nova.rotulo()
                        + ". Inclua as especialidades nela em vez de repetir a categoria.");
            }
            if (outra.especialidadeIds().equals(nova.especialidadeIds())) {
                throw new EspecialidadeInvalidaException("Você já tem a atuação " + nova.rotulo() + ".");
            }
        }
    }

}
