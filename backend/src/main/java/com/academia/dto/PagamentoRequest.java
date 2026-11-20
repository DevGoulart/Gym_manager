package com.academia.dto;

import java.time.LocalDate;

public record PagamentoRequest(String formaPagamento, LocalDate dataPagamento) {
}
