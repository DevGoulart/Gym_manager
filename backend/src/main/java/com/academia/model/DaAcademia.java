package com.academia.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

/**
 * Base das entidades que pertencem a uma academia (multi-academia).
 * Cada tabela ganha a coluna academia_id, e os services sempre filtram por ela:
 * uma academia nunca enxerga os dados de outra.
 */
@MappedSuperclass
public abstract class DaAcademia {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academia_id")
    private Academia academia;

    public Academia getAcademia() { return academia; }
    public void setAcademia(Academia academia) { this.academia = academia; }
}
