package com.quickcare.backend.especialidade;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EspecialidadeRepository extends JpaRepository<Especialidade, Long> {

    List<Especialidade> findAllByIdIn(List<Long> ids);

}
