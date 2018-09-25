package com.jacobsonmt.idrbind.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public final class IDRBindJobResult {

    private final String resultPDB;
    private final String resultCSV;

}
