package com.lucas.sistemabancario.repository;

import com.lucas.sistemabancario.entity.Transacao;
import com.lucas.sistemabancario.entity.enums.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {
    List<Transacao> findByContaId(Long contaId);

    boolean existsByContaIdAndTipoTransacaoAndDataHoraBetween(
            Long contaId,
            TipoTransacao tipoTransacao,
            LocalDateTime inicioDoDia,
            LocalDateTime fimDoDia
    );
}
